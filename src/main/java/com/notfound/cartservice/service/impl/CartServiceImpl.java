package com.notfound.cartservice.service.impl;

import com.notfound.cartservice.client.BookServiceClient;
import com.notfound.cartservice.client.dto.BatchBookRequest;
import com.notfound.cartservice.client.dto.BatchBookResponse;
import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.client.dto.BookResponse;
import com.notfound.cartservice.client.dto.BookServiceApiResponse;
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
import com.notfound.cartservice.repository.CartItemRepository;
import com.notfound.cartservice.repository.CartRepository;
import com.notfound.cartservice.service.CartService;
import feign.FeignException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final String ADD_TO_CART_LOG_KEY = "CART_ADD_FLOW";
    private static final long ADD_TO_CART_SLOW_THRESHOLD_MS = 3_000L;

    private final RedisTemplate<String, CartCache> redisTemplate;
    private final BookServiceClient bookServiceClient;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EntityManager entityManager;
    private final CartMapper cartMapper;
    private final CartCacheMapper cartCacheMapper;

    @Value("${cart.ttl-days:7}")
    private long ttlDays;

    @Value("${cart.max-items-per-cart:100}")
    private int maxItemsPerCart;

    @Override
    public CartResponse getCart(UUID userId) {
        CartEntity cart = loadOrCreate(userId, true);
        return cartMapper.toCartResponse(cart);
    }

    @Override
    @Transactional
    public AddToCartResponse addToCart(UUID userId, AddToCartRequest request) {
        String traceId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();

        log.info("{} stage=start traceId={} userId={} bookId={} quantity={}",
                ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), request.getQuantity());

        try {
            long stageStartedAt = System.nanoTime();
            CartEntity cart = loadOrCreate(userId, false);
            detachCartGraph(cart);
            log.info("{} stage=load_cart_done traceId={} userId={} bookId={} durationMs={} cartId={} itemCount={}",
                    ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), elapsedMs(stageStartedAt),
                    cart.getCartId(), cart.lineItemCount());

            stageStartedAt = System.nanoTime();
            BookResponse book = fetchBookBatch(request.getBookId());
            log.info("{} stage=fetch_book_done traceId={} userId={} bookId={} durationMs={} stock={}",
                    ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), elapsedMs(stageStartedAt),
                    book.getStock());
            validateStock(book, request.getQuantity());

            Optional<CartItemEntity> existing = cart.getItems().stream()
                    .filter(i -> i.getBookId().equals(request.getBookId()))
                    .findFirst();

            CartItemEntity item;
            boolean existingItem = existing.isPresent();
            if (existingItem) {
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
            stageStartedAt = System.nanoTime();
            persistCartItemChange(cart, item, existingItem);
            log.info("{} stage=save_db_done traceId={} userId={} bookId={} durationMs={} existingItem={} itemCount={}",
                    ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), elapsedMs(stageStartedAt),
                    existingItem, cart.lineItemCount());

            stageStartedAt = System.nanoTime();
            writeCacheSafely(cart);
            log.info("{} stage=write_cache_done traceId={} userId={} bookId={} durationMs={} existingItem={} itemCount={}",
                    ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), elapsedMs(stageStartedAt),
                    existingItem, cart.lineItemCount());

            AddToCartResponse response = AddToCartResponse.builder()
                    .cartItem(cartMapper.toCartItemResponse(item))
                    .cartItemCount(cart.lineItemCount())
                    .build();

            long totalDurationMs = elapsedMs(startedAt);
            if (totalDurationMs >= ADD_TO_CART_SLOW_THRESHOLD_MS) {
                log.warn("{} stage=slow_success traceId={} userId={} bookId={} quantity={} totalDurationMs={} thresholdMs={}",
                        ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), request.getQuantity(),
                        totalDurationMs, ADD_TO_CART_SLOW_THRESHOLD_MS);
            } else {
                log.info("{} stage=success traceId={} userId={} bookId={} quantity={} totalDurationMs={}",
                        ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), request.getQuantity(),
                        totalDurationMs);
            }

            return response;
        } catch (RuntimeException ex) {
            log.warn("{} stage=failed traceId={} userId={} bookId={} quantity={} totalDurationMs={} errorType={} errorMessage={}",
                    ADD_TO_CART_LOG_KEY, traceId, userId, request.getBookId(), request.getQuantity(),
                    elapsedMs(startedAt), ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    @Transactional
    public UpdateCartResponse updateCartItem(UUID userId, UUID bookId, UpdateCartItemRequest request) {
        CartEntity cart = loadOrCreate(userId, false);
        detachCartGraph(cart);
        CartItemEntity item = cart.getItems().stream()
                .filter(i -> bookId.equals(i.getBookId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", bookId));

        BookResponse book = fetchBookBatch(bookId);
        validateStock(book, request.getQuantity());

        item.setQuantity(request.getQuantity());
        applyBookDetails(item, book);

        cart.setUpdatedAt(Instant.now());
        persistCartItemChange(cart, item, true);
        writeCacheSafely(cart);
        log.info("Update bookId={} qty={} cho userId={}", bookId, request.getQuantity(), userId);

        return UpdateCartResponse.builder()
                .cartItem(cartMapper.toCartItemResponse(item))
                .totalPrice(cart.totalPrice())
                .build();
    }

    @Override
    @Transactional
    public RemoveCartResponse removeFromCart(UUID userId, UUID bookId) {
        CartEntity cart = loadOrCreate(userId, false);
        detachCartGraph(cart);
        boolean removed = cart.getItems().removeIf(i -> bookId.equals(i.getBookId()));
        if (!removed) {
            throw new ResourceNotFoundException("CartItem", bookId);
        }

        cart.setUpdatedAt(Instant.now());
        cartItemRepository.deleteByCartIdAndBookId(cart.getCartId(), bookId);
        cartRepository.updateUpdatedAt(cart.getCartId(), cart.getUpdatedAt());
        writeCacheSafely(cart);
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteCacheSafely(userId);
                }
            });
        } else {
            deleteCacheSafely(userId);
        }
        log.info("Clear cart cho userId={}", userId);
    }

    @Override
    @Transactional
    public void clearCartItems(UUID userId, List<UUID> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return;
        }

        CartEntity cart = loadOrCreate(userId, false);
        detachCartGraph(cart);
        Set<UUID> targetBookIds = Set.copyOf(bookIds);
        boolean removed = cart.getItems().removeIf(item -> targetBookIds.contains(item.getBookId()));
        if (!removed) {
            log.info("No checkout cart items to clear for userId={} bookIds={}", userId, targetBookIds);
            return;
        }

        cart.setUpdatedAt(Instant.now());
        cartItemRepository.deleteByCartIdAndBookIdIn(cart.getCartId(), targetBookIds);
        cartRepository.updateUpdatedAt(cart.getCartId(), cart.getUpdatedAt());
        writeCacheSafely(cart);
        log.info("Clear selected cart items for userId={} bookIds={}", userId, targetBookIds);
    }

    @Override
    public boolean isCartEmpty(UUID userId) {
        CartEntity cart = loadOrCreate(userId, false);
        return cart.getItems().isEmpty();
    }

    @Override
    public CartSnapshotResponse getSnapshot(UUID userId) {
        CartEntity cart = loadOrCreate(userId, false);

        Map<UUID, BookResponse> booksById = fetchSnapshotBooksSafely(cart);
        List<CartSnapshotResponse.SnapshotItem> snapshotItems = new ArrayList<>();
        for (CartItemEntity item : cart.getItems()) {
            try {
                BookResponse book = booksById.get(item.getBookId());
                if (book == null) {
                    throw new ResourceNotFoundException("Book", item.getBookId());
                }
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
        CartEntity cart = loadOrCreate(userId, false);
        return cart.lineItemCount();
    }

    @Override
    public boolean isBookInCart(UUID userId, UUID bookId) {
        CartEntity cart = loadOrCreate(userId, false);
        return cart.getItems().stream().anyMatch(i -> bookId.equals(i.getBookId()));
    }

    private CartEntity loadOrCreate(UUID userId, boolean refreshMissingImages) {
        CartCache cached = readCacheSafely(userId);
        if (cached != null) {
            CartEntity fromCache = cartCacheMapper.toEntity(cached);
            if (fromCache.getItems() == null) {
                fromCache.setItems(new ArrayList<>());
            }
            if (refreshMissingImages) {
                refreshMissingBookImages(fromCache);
            }
            return fromCache;
        }

        Optional<CartEntity> fromDb = cartRepository.findByUserId(userId);
        if (fromDb.isPresent()) {
            CartEntity cart = fromDb.get();
            if (cart.getItems() == null) {
                cart.setItems(new ArrayList<>());
            }
            if (refreshMissingImages) {
                refreshMissingBookImages(cart);
            }
            writeCacheSafely(cart);
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
        writeCacheSafely(saved);
        return saved;
    }

    private void persistCartItemChange(CartEntity cart, CartItemEntity item, boolean existingItem) {
        if (existingItem) {
            updateCartItemRow(item);
        } else {
            item.setCart(cartRepository.getReferenceById(cart.getCartId()));
            CartItemEntity savedItem = cartItemRepository.save(item);
            item.setCart(cart);
            if (savedItem != null) {
                item.setItemId(savedItem.getItemId());
            }
        }
        cartRepository.updateUpdatedAt(cart.getCartId(), cart.getUpdatedAt());
    }

    private void updateCartItemRow(CartItemEntity item) {
        cartItemRepository.updateItemDetails(
                item.getItemId(),
                item.getQuantity(),
                item.getBookTitle(),
                item.getBookIsbn(),
                item.getBookPrice(),
                item.getBookDiscountPrice(),
                item.getBookImageUrl(),
                item.getStockQuantity()
        );
    }

    private void detachCartGraph(CartEntity cart) {
        if (cart == null) {
            return;
        }
        if (cart.getItems() != null) {
            cart.getItems().forEach(item -> {
                if (item != null && entityManager.contains(item)) {
                    entityManager.detach(item);
                }
            });
        }
        if (entityManager.contains(cart)) {
            entityManager.detach(cart);
        }
    }

    private CartCache readCacheSafely(UUID userId) {
        try {
            return redisTemplate.opsForValue().get(key(userId));
        } catch (Exception e) {
            log.warn("Redis read lỗi cho userId={}, fallback MySQL: {}", userId, e.getMessage());
            return null;
        }
    }

    private void writeCacheSafely(CartEntity cart) {
        try {
            CartCache cache = cartCacheMapper.toCache(cart);
            redisTemplate.opsForValue().set(key(cart.getUserId()), cache, Duration.ofDays(ttlDays));
        } catch (Exception e) {
            log.warn("Redis write lỗi cho userId={}, bỏ qua: {}", cart.getUserId(), e.getMessage());
        }
    }

    private void deleteCacheSafely(UUID userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception e) {
            log.warn("Redis delete lỗi cho userId={}, bỏ qua: {}", userId, e.getMessage());
        }
    }

    private String key(UUID userId) {
        return CART_KEY_PREFIX + userId;
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
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
        List<CartItemEntity> changedItems = new ArrayList<>();
        Map<UUID, BookResponse> booksById = fetchSnapshotBooksSafely(cart);
        for (CartItemEntity item : cart.getItems()) {
            try {
                BookResponse book = booksById.get(item.getBookId());
                if (book == null) {
                    throw new ResourceNotFoundException("Book", item.getBookId());
                }
                if (applyLatestBookDetails(item, book)) {
                    changedItems.add(item);
                }
            } catch (Exception ex) {
                log.warn("Không refresh được thông tin sách cho bookId={}: {}", item.getBookId(), ex.getMessage());
            }
        }
        if (!changedItems.isEmpty()) {
            changedItems.forEach(this::updateCartItemRow);
            writeCacheSafely(cart);
        }
    }

    private boolean applyLatestBookDetails(CartItemEntity item, BookResponse book) {
        boolean changed = false;
        if (book.getTitle() != null && !Objects.equals(item.getBookTitle(), book.getTitle())) {
            item.setBookTitle(book.getTitle());
            changed = true;
        }
        if (book.getIsbn() != null && !Objects.equals(item.getBookIsbn(), book.getIsbn())) {
            item.setBookIsbn(book.getIsbn());
            changed = true;
        }

        if (book.getPrice() != null) {
            Double price = book.getPriceAsDouble();
            if (!Objects.equals(item.getBookPrice(), price)) {
                item.setBookPrice(price);
                changed = true;
            }
        }

        Double discountPrice = book.getDiscountPriceAsDouble();
        if (!Objects.equals(item.getBookDiscountPrice(), discountPrice)) {
            item.setBookDiscountPrice(discountPrice);
            changed = true;
        }

        String imageUrl = book.resolveImageUrl();
        if (imageUrl != null && !imageUrl.isBlank() && !Objects.equals(item.getBookImageUrl(), imageUrl)) {
            item.setBookImageUrl(imageUrl);
            changed = true;
        }

        if (!Objects.equals(item.getStockQuantity(), book.getStock())) {
            item.setStockQuantity(book.getStock());
            changed = true;
        }
        return changed;
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

    private BookResponse fetchBookBatch(UUID bookId) {
        try {
            BatchBookRequest request = BatchBookRequest.builder()
                    .ids(List.of(bookId.toString()))
                    .build();
            BookServiceApiResponse<BatchBookResponse> resp = bookServiceClient.getBooksBatch(request);
            BatchBookResponse batch = resp == null ? null : resp.getData();
            BatchBookResponse.BookItem item = batch == null || batch.getItems() == null
                    ? null
                    : batch.getItems().stream()
                    .filter(book -> bookId.equals(book.getId()))
                    .findFirst()
                    .orElse(null);

            if (item == null) {
                throw new ResourceNotFoundException("Book", bookId);
            }
            if (Boolean.FALSE.equals(item.getInStock())) {
                throw new BookOutOfStockException(bookId, 0);
            }

            return BookResponse.builder()
                    .id(item.getId())
                    .title(item.getTitle())
                    .price(item.getPrice())
                    .discountPrice(item.getDiscountPrice())
                    .stock(item.getStockQuantity())
                    .coverUrl(item.getThumbnailUrl())
                    .mainImageUrl(item.getThumbnailUrl())
                    .build();
        } catch (FeignException.NotFound nf) {
            throw new ResourceNotFoundException("Book", bookId);
        } catch (FeignException fe) {
            log.error("Feign error khi gọi book-service batch bookId={}: status={}", bookId, fe.status());
            throw new ServiceUnavailableException("book-service");
        } catch (RuntimeException re) {
            if (re instanceof ResourceNotFoundException || re instanceof BookOutOfStockException) {
                throw re;
            }
            log.error("Lỗi khi gọi book-service batch bookId={}", bookId, re);
            throw new ServiceUnavailableException("book-service");
        }
    }

    private Map<UUID, BookResponse> fetchSnapshotBooksSafely(CartEntity cart) {
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return Map.of();
        }

        List<UUID> bookIds = cart.getItems().stream()
                .map(CartItemEntity::getBookId)
                .distinct()
                .toList();

        try {
            BatchBookRequest request = BatchBookRequest.builder()
                    .ids(bookIds.stream().map(UUID::toString).toList())
                    .build();
            BookServiceApiResponse<BatchBookResponse> resp = bookServiceClient.getBooksBatch(request);
            BatchBookResponse batch = resp == null ? null : resp.getData();
            if (batch == null || batch.getItems() == null) {
                return Map.of();
            }

            Map<UUID, BookResponse> booksById = new HashMap<>();
            for (BatchBookResponse.BookItem item : batch.getItems()) {
                if (item != null && item.getId() != null) {
                    booksById.put(item.getId(), BookResponse.builder()
                            .id(item.getId())
                            .title(item.getTitle())
                            .price(item.getPrice())
                            .discountPrice(item.getDiscountPrice())
                            .stock(item.getStockQuantity() != null
                                    ? item.getStockQuantity()
                                    : Boolean.FALSE.equals(item.getInStock()) ? 0 : null)
                            .coverUrl(item.getThumbnailUrl())
                            .mainImageUrl(item.getThumbnailUrl())
                            .build());
                }
            }
            return booksById;
        } catch (RuntimeException ex) {
            log.warn("Snapshot batch fetch lỗi cho userId={}, fallback cart cache: {}", cart.getUserId(), ex.getMessage());
            return Map.of();
        }
    }

    private void validateStock(BookResponse book, int quantity) {
        if (book.getStock() != null && book.getStock() < quantity) {
            throw new BookOutOfStockException(book.getId(), book.getStock());
        }
    }
}
