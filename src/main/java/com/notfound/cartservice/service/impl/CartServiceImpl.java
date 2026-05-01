package com.notfound.cartservice.service.impl;

import com.notfound.cartservice.client.BookServiceClient;
import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.client.dto.BookResponse;
import com.notfound.cartservice.exception.BookOutOfStockException;
import com.notfound.cartservice.exception.ResourceNotFoundException;
import com.notfound.cartservice.exception.ServiceUnavailableException;
import com.notfound.cartservice.model.dto.request.AddCartItemRequest;
import com.notfound.cartservice.model.dto.request.UpdateCartItemRequest;
import com.notfound.cartservice.model.dto.response.CartItemResponse;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.dto.response.CartSnapshotResponse;
import com.notfound.cartservice.model.entity.Cart;
import com.notfound.cartservice.model.entity.CartItem;
import com.notfound.cartservice.service.CartService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private static final String CART_KEY_PREFIX = "cart:";

    private final RedisTemplate<String, Cart> redisTemplate;
    private final BookServiceClient bookServiceClient;

    @Value("${cart.ttl-days:7}")
    private long ttlDays;

    @Value("${cart.max-items-per-cart:100}")
    private int maxItemsPerCart;

    @Override
    public CartResponse getCart(UUID userId) {
        Cart cart = loadOrCreate(userId);
        return toResponse(cart);
    }

    @Override
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        Cart cart = loadOrCreate(userId);

        BookResponse book = fetchBook(request.getBookId());
        validateStock(book, request.getQuantity());

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getBookId().equals(request.getBookId()))
                .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            validateStock(book, newQuantity);
            item.setQuantity(newQuantity);
            item.setBookPrice(book.getPrice());
            item.setBookTitle(book.getTitle());
            item.setBookCoverUrl(book.getCoverUrl());
        } else {
            if (cart.getItems().size() >= maxItemsPerCart) {
                throw new IllegalArgumentException(
                        "Giỏ hàng đã đạt giới hạn " + maxItemsPerCart + " loại sản phẩm");
            }
            CartItem item = CartItem.builder()
                    .itemId(UUID.randomUUID().toString())
                    .bookId(book.getId())
                    .bookTitle(book.getTitle())
                    .bookPrice(book.getPrice())
                    .bookCoverUrl(book.getCoverUrl())
                    .quantity(request.getQuantity())
                    .addedAt(Instant.now())
                    .build();
            cart.getItems().add(item);
        }

        cart.setUpdatedAt(Instant.now());
        save(cart);
        log.info("Add item bookId={} qty={} cho userId={}", request.getBookId(), request.getQuantity(), userId);
        return toResponse(cart);
    }

    @Override
    public CartResponse updateItem(UUID userId, String itemId, UpdateCartItemRequest request) {
        Cart cart = loadOrCreate(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> itemId.equals(i.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        BookResponse book = fetchBook(item.getBookId());
        validateStock(book, request.getQuantity());

        item.setQuantity(request.getQuantity());
        item.setBookPrice(book.getPrice());
        item.setBookTitle(book.getTitle());
        item.setBookCoverUrl(book.getCoverUrl());

        cart.setUpdatedAt(Instant.now());
        save(cart);
        log.info("Update itemId={} qty={} cho userId={}", itemId, request.getQuantity(), userId);
        return toResponse(cart);
    }

    @Override
    public CartResponse removeItem(UUID userId, String itemId) {
        Cart cart = loadOrCreate(userId);
        boolean removed = cart.getItems().removeIf(i -> itemId.equals(i.getItemId()));
        if (!removed) {
            throw new ResourceNotFoundException("CartItem", itemId);
        }
        cart.setUpdatedAt(Instant.now());
        save(cart);
        log.info("Remove itemId={} khỏi cart của userId={}", itemId, userId);
        return toResponse(cart);
    }

    @Override
    public void clearCart(UUID userId) {
        String key = key(userId);
        Boolean existed = redisTemplate.delete(key);
        log.info("Clear cart cho userId={} (existed={})", userId, existed);
    }

    @Override
    public CartSnapshotResponse getSnapshot(UUID userId) {
        Cart cart = loadOrCreate(userId);

        List<CartSnapshotResponse.SnapshotItem> snapshotItems = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            try {
                BookResponse book = fetchBook(item.getBookId());
                snapshotItems.add(CartSnapshotResponse.SnapshotItem.builder()
                        .bookId(book.getId())
                        .bookTitle(book.getTitle())
                        .bookPrice(book.getPrice())
                        .bookCoverUrl(book.getCoverUrl())
                        .quantity(item.getQuantity())
                        .subtotal(book.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))
                        .build());
            } catch (Exception ex) {
                log.warn("Snapshot bỏ qua item bookId={} (lỗi fetch book): {}", item.getBookId(), ex.getMessage());
                snapshotItems.add(CartSnapshotResponse.SnapshotItem.builder()
                        .bookId(item.getBookId())
                        .bookTitle(item.getBookTitle())
                        .bookPrice(item.getBookPrice())
                        .bookCoverUrl(item.getBookCoverUrl())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .build());
            }
        }

        java.math.BigDecimal total = snapshotItems.stream()
                .map(CartSnapshotResponse.SnapshotItem::getSubtotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        return CartSnapshotResponse.builder()
                .userId(userId)
                .items(snapshotItems)
                .totalPrice(total)
                .snapshotAt(Instant.now())
                .build();
    }

    @Override
    public int countItems(UUID userId) {
        Cart cart = loadOrCreate(userId);
        return cart.totalItems();
    }

    @Override
    public boolean isBookInCart(UUID userId, UUID bookId) {
        Cart cart = loadOrCreate(userId);
        return cart.getItems().stream().anyMatch(i -> bookId.equals(i.getBookId()));
    }

    private Cart loadOrCreate(UUID userId) {
        Cart cart = redisTemplate.opsForValue().get(key(userId));
        if (cart == null) {
            cart = Cart.builder()
                    .userId(userId)
                    .items(new ArrayList<>())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        }
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }
        return cart;
    }

    private void save(Cart cart) {
        redisTemplate.opsForValue().set(key(cart.getUserId()), cart, Duration.ofDays(ttlDays));
    }

    private String key(UUID userId) {
        return CART_KEY_PREFIX + userId.toString();
    }

    private BookResponse fetchBook(UUID bookId) {
        try {
            BookApiResponse resp = bookServiceClient.getBook(bookId);
            if (resp == null || resp.getResult() == null) {
                throw new ResourceNotFoundException("Book", bookId);
            }
            BookResponse book = resp.getResult();
            if (book.getPrice() == null) {
                book.setPrice(java.math.BigDecimal.ZERO);
            }
            return book;
        } catch (FeignException.NotFound nf) {
            throw new ResourceNotFoundException("Book", bookId);
        } catch (FeignException fe) {
            log.error("Feign error khi gọi book-service bookId={}: status={}", bookId, fe.status());
            throw new ServiceUnavailableException("book-service");
        } catch (RuntimeException re) {
            if (re instanceof ResourceNotFoundException) throw re;
            log.error("Lỗi khi gọi book-service bookId={}", bookId, re);
            throw new ServiceUnavailableException("book-service");
        }
    }

    private void validateStock(BookResponse book, int quantity) {
        if (book.getStock() != null && book.getStock() < quantity) {
            throw new BookOutOfStockException(book.getId(), book.getStock());
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(i -> CartItemResponse.builder()
                        .itemId(i.getItemId())
                        .bookId(i.getBookId())
                        .bookTitle(i.getBookTitle())
                        .bookPrice(i.getBookPrice())
                        .bookCoverUrl(i.getBookCoverUrl())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .addedAt(i.getAddedAt())
                        .build())
                .toList();

        return CartResponse.builder()
                .userId(cart.getUserId())
                .items(items)
                .itemCount(cart.totalItems())
                .totalPrice(cart.totalPrice())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
