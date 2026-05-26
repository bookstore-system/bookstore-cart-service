package com.notfound.cartservice.service.impl;

import com.notfound.cartservice.client.BookServiceClient;
import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.client.dto.BookResponse;
import com.notfound.cartservice.exception.BookOutOfStockException;
import com.notfound.cartservice.exception.ResourceNotFoundException;
import com.notfound.cartservice.exception.ServiceUnavailableException;
import com.notfound.cartservice.mapper.CartCacheMapper;
import com.notfound.cartservice.mapper.CartMapper;
import com.notfound.cartservice.model.cache.CartCache;
import com.notfound.cartservice.model.dto.request.AddToCartRequest;
import com.notfound.cartservice.model.dto.request.UpdateCartItemRequest;
import com.notfound.cartservice.model.dto.response.AddToCartResponse;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.dto.response.CartSnapshotResponse;
import com.notfound.cartservice.model.dto.response.RemoveCartResponse;
import com.notfound.cartservice.model.dto.response.UpdateCartResponse;
import com.notfound.cartservice.model.entity.CartEntity;
import com.notfound.cartservice.model.entity.CartItemEntity;
import com.notfound.cartservice.repository.CartRepository;
import com.notfound.cartservice.service.CartService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private final RedisTemplate<String, CartCache> redisTemplate;
    private final BookServiceClient bookServiceClient;
    private final CartRepository cartRepository;
    private final CartMapper cartMapper;
    private final CartCacheMapper cartCacheMapper;

    @Value("${cart.ttl-days:7}")
    private long ttlDays;

    @Value("${cart.max-items-per-cart:100}")
    private int maxItemsPerCart;

    @Override
    public CartResponse getCart(UUID userId) {
        CartEntity cart = loadOrCreate(userId);
        return cartMapper.toCartResponse(cart);
    }

    @Override
    public AddToCartResponse addToCart(UUID userId, AddToCartRequest request) {
        CartEntity cart = loadOrCreate(userId);
        BookResponse book = fetchBook(request.getBookId());
        validateStock(book, request.getQuantity());

        Optional<CartItemEntity> existing = cart.getItems().stream()
                .filter(i -> i.getBookId().equals(request.getBookId()))
                .findFirst();

        CartItemEntity item;
        if (existing.isPresent()) {
            item = existing.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            validateStock(book, newQuantity);
            item.setQuantity(newQuantity);
            applyBookDetails(item, book);
        } else {
            if (cart.getItems().size() >= maxItemsPerCart) {
                throw new IllegalArgumentException(
                        "Giỏ hàng đã đạt giới hạn " + maxItemsPerCart + " loại sản phẩm");
            }
            item = CartItemEntity.builder()
                    .itemId(UUID.randomUUID())
                    .bookId(book.getId())
                    .quantity(request.getQuantity())
                    .addedAt(Instant.now())
                    .build();
            item.setCart(cart);
            applyBookDetails(item, book);
            cart.getItems().add(item);
        }

        cart.setUpdatedAt(Instant.now());
        save(cart);
        log.info("Add bookId={} qty={} cho userId={}", request.getBookId(), request.getQuantity(), userId);

        return AddToCartResponse.builder()
                .cartItem(cartMapper.toCartItemResponse(item))
                .cartItemCount(cart.lineItemCount())
                .build();
    }

    @Override
    public UpdateCartResponse updateCartItem(UUID userId, UUID bookId, UpdateCartItemRequest request) {
        CartEntity cart = loadOrCreate(userId);
        CartItemEntity item = cart.getItems().stream()
                .filter(i -> bookId.equals(i.getBookId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", bookId));

        BookResponse book = fetchBook(bookId);
        validateStock(book, request.getQuantity());

        item.setQuantity(request.getQuantity());
        applyBookDetails(item, book);

        cart.setUpdatedAt(Instant.now());
        save(cart);
        log.info("Update bookId={} qty={} cho userId={}", bookId, request.getQuantity(), userId);

        return UpdateCartResponse.builder()
                .cartItem(cartMapper.toCartItemResponse(item))
                .totalPrice(cart.totalPrice())
                .build();
    }

    @Override
    public RemoveCartResponse removeFromCart(UUID userId, UUID bookId) {
        CartEntity cart = loadOrCreate(userId);
        boolean removed = cart.getItems().removeIf(i -> bookId.equals(i.getBookId()));
        if (!removed) {
            throw new ResourceNotFoundException("CartItem", bookId);
        }

        cart.setUpdatedAt(Instant.now());
        save(cart);
        log.info("Remove bookId={} khỏi cart của userId={}", bookId, userId);

        return RemoveCartResponse.builder()
                .cartItemCount(cart.lineItemCount())
                .totalPrice(cart.totalPrice())
                .build();
    }

    @Override
    @Transactional
    public void clearCart(UUID userId) {
        cartRepository.deleteByUserId(userId);
        redisTemplate.delete(key(userId));
        log.info("Clear cart cho userId={}", userId);
    }

    @Override
    public boolean isCartEmpty(UUID userId) {
        CartEntity cart = loadOrCreate(userId);
        return cart.getItems().isEmpty();
    }

    @Override
    public CartSnapshotResponse getSnapshot(UUID userId) {
        CartEntity cart = loadOrCreate(userId);

        List<CartSnapshotResponse.SnapshotItem> snapshotItems = new ArrayList<>();
        for (CartItemEntity item : cart.getItems()) {
            try {
                BookResponse book = fetchBook(item.getBookId());
                BigDecimal price = BigDecimal.valueOf(book.getPriceAsDouble());
                snapshotItems.add(CartSnapshotResponse.SnapshotItem.builder()
                        .bookId(book.getId())
                        .bookTitle(book.getTitle())
                        .bookPrice(price)
                        .bookCoverUrl(book.resolveImageUrl())
                        .quantity(item.getQuantity())
                        .subtotal(price.multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build());
            } catch (Exception ex) {
                log.warn("Snapshot bỏ qua item bookId={} (lỗi fetch book): {}", item.getBookId(), ex.getMessage());
                BigDecimal price = item.getBookPrice() == null
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(item.getBookPrice());
                snapshotItems.add(CartSnapshotResponse.SnapshotItem.builder()
                        .bookId(item.getBookId())
                        .bookTitle(item.getBookTitle())
                        .bookPrice(price)
                        .bookCoverUrl(item.getBookImageUrl())
                        .quantity(item.getQuantity())
                        .subtotal(price.multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build());
            }
        }

        BigDecimal total = snapshotItems.stream()
                .map(CartSnapshotResponse.SnapshotItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartSnapshotResponse.builder()
                .userId(userId)
                .items(snapshotItems)
                .totalPrice(total)
                .snapshotAt(Instant.now())
                .build();
    }

    @Override
    public Long getCartItemCount(UUID userId) {
        CartEntity cart = loadOrCreate(userId);
        return cart.lineItemCount();
    }

    @Override
    public boolean isBookInCart(UUID userId, UUID bookId) {
        CartEntity cart = loadOrCreate(userId);
        return cart.getItems().stream().anyMatch(i -> bookId.equals(i.getBookId()));
    }

    private CartEntity loadOrCreate(UUID userId) {
        CartCache cached = redisTemplate.opsForValue().get(key(userId));
        if (cached != null) {
            CartEntity fromCache = cartCacheMapper.toEntity(cached);
            if (fromCache.getItems() == null) {
                fromCache.setItems(new ArrayList<>());
            }
            refreshMissingBookImages(fromCache);
            return fromCache;
        }

        Optional<CartEntity> fromDb = cartRepository.findByUserId(userId);
        if (fromDb.isPresent()) {
            CartEntity cart = fromDb.get();
            if (cart.getItems() == null) {
                cart.setItems(new ArrayList<>());
            }
            refreshMissingBookImages(cart);
            writeCache(cart);
            return cart;
        }

        Instant now = Instant.now();
        CartEntity cart = CartEntity.builder()
                .cartId(UUID.randomUUID())
                .userId(userId)
                .items(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
        CartEntity saved = cartRepository.save(cart);
        writeCache(saved);
        return saved;
    }

    private void save(CartEntity cart) {
        CartEntity saved = cartRepository.save(cart);
        writeCache(saved != null ? saved : cart);
    }

    private void writeCache(CartEntity cart) {
        CartCache cache = cartCacheMapper.toCache(cart);
        redisTemplate.opsForValue().set(key(cart.getUserId()), cache, Duration.ofDays(ttlDays));
    }

    private String key(UUID userId) {
        return CART_KEY_PREFIX + userId;
    }

    private void applyBookDetails(CartItemEntity item, BookResponse book) {
        item.setBookTitle(book.getTitle());
        item.setBookIsbn(book.getIsbn());
        item.setBookPrice(book.getPriceAsDouble());
        item.setBookDiscountPrice(book.getDiscountPriceAsDouble());
        item.setBookImageUrl(book.resolveImageUrl());
        item.setStockQuantity(book.getStock());
    }

    private void refreshMissingBookImages(CartEntity cart) {
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return;
        }
        boolean changed = false;
        for (CartItemEntity item : cart.getItems()) {
            if (item.getBookImageUrl() == null || item.getBookImageUrl().isBlank()) {
                try {
                    BookResponse book = fetchBook(item.getBookId());
                    applyBookDetails(item, book);
                    changed = true;
                } catch (Exception ex) {
                    log.warn("Không refresh được ảnh cho bookId={}: {}", item.getBookId(), ex.getMessage());
                }
            }
        }
        if (changed) {
            save(cart);
        }
    }

    private BookResponse fetchBook(UUID bookId) {
        try {
            BookApiResponse resp = bookServiceClient.getBook(bookId);
            BookResponse book = resp == null ? null : resp.getBook();
            if (book == null) {
                throw new ResourceNotFoundException("Book", bookId);
            }
            return book;
        } catch (FeignException.NotFound nf) {
            throw new ResourceNotFoundException("Book", bookId);
        } catch (FeignException fe) {
            log.error("Feign error khi gọi book-service bookId={}: status={}", bookId, fe.status());
            throw new ServiceUnavailableException("book-service");
        } catch (RuntimeException re) {
            if (re instanceof ResourceNotFoundException) {
                throw re;
            }
            log.error("Lỗi khi gọi book-service bookId={}", bookId, re);
            throw new ServiceUnavailableException("book-service");
        }
    }

    private void validateStock(BookResponse book, int quantity) {
        if (book.getStock() != null && book.getStock() < quantity) {
            throw new BookOutOfStockException(book.getId(), book.getStock());
        }
    }
}
