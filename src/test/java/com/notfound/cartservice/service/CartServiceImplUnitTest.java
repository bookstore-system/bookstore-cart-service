package com.notfound.cartservice.service;

import com.notfound.cartservice.client.BookServiceClient;
import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.client.dto.BookResponse;
import com.notfound.cartservice.exception.BookOutOfStockException;
import com.notfound.cartservice.exception.ResourceNotFoundException;
import com.notfound.cartservice.mapper.CartCacheMapper;
import com.notfound.cartservice.mapper.CartMapper;
import com.notfound.cartservice.model.cache.CartCache;
import com.notfound.cartservice.model.cache.CartItemCache;
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
import com.notfound.cartservice.service.impl.CartServiceImpl;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceImplUnitTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOOK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CART_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    RedisTemplate<String, CartCache> redisTemplate;
    @Mock
    ValueOperations<String, CartCache> valueOps;
    @Mock
    BookServiceClient bookServiceClient;
    @Mock
    CartRepository cartRepository;

    CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(
                redisTemplate,
                bookServiceClient,
                cartRepository,
                new CartMapper(),
                new CartCacheMapper()
        );
        ReflectionTestUtils.setField(cartService, "ttlDays", 7L);
        ReflectionTestUtils.setField(cartService, "maxItemsPerCart", 2);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getCart_cacheHit_returnsWithoutDb() {
        CartCache cache = emptyCache();
        when(valueOps.get(cartKey())).thenReturn(cache);

        CartResponse resp = cartService.getCart(USER_ID);

        assertEquals(USER_ID, resp.getUserId());
        assertEquals(0L, resp.getItemCount());
        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    void getCart_cacheMiss_dbHit_writesCache() {
        when(valueOps.get(cartKey())).thenReturn(null);
        CartEntity entity = persistedEmptyCart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        cartService.getCart(USER_ID);

        verify(valueOps).set(eq(cartKey()), any(CartCache.class), any(java.time.Duration.class));
    }

    @Test
    void getCart_cacheMiss_dbMiss_createsAndPersists() {
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse resp = cartService.getCart(USER_ID);

        assertNotNull(resp.getCartId());
        verify(cartRepository).save(any(CartEntity.class));
        verify(valueOps).set(eq(cartKey()), any(CartCache.class), any(java.time.Duration.class));
    }

    @Test
    void addToCart_usesMainImageUrlWhenCoverUrlMissing() {
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse book = BookResponse.builder()
                .id(BOOK_ID)
                .title("Sách Test")
                .price(new BigDecimal("50000"))
                .stock(10)
                .mainImageUrl("https://res.cloudinary.com/demo/book.jpg")
                .build();
        BookApiResponse api = new BookApiResponse();
        api.setCode(200);
        api.setResult(book);
        when(bookServiceClient.getBook(BOOK_ID)).thenReturn(api);

        AddToCartResponse resp = cartService.addToCart(USER_ID, new AddToCartRequest(BOOK_ID, 1));

        assertEquals("https://res.cloudinary.com/demo/book.jpg", resp.getCartItem().getBookImageUrl());
    }

    @Test
    void addToCart_firstItem_savesDbAndCache() {
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        stubBook(BOOK_ID, 10, new BigDecimal("50000"));

        AddToCartResponse resp = cartService.addToCart(USER_ID, new AddToCartRequest(BOOK_ID, 2));

        assertEquals(BOOK_ID, resp.getCartItem().getBookId());
        assertEquals(2, resp.getCartItem().getQuantity());
        assertEquals(1L, resp.getCartItemCount());
        verify(cartRepository, Mockito.atLeast(2)).save(any(CartEntity.class));
        verify(valueOps, Mockito.atLeast(2)).set(eq(cartKey()), any(CartCache.class), any(java.time.Duration.class));
    }

    @Test
    void addToCart_duplicateBookId_accumulatesQuantity() {
        CartEntity cart = cartWithItem(BOOK_ID, 1);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        stubBook(BOOK_ID, 10, new BigDecimal("50000"));

        AddToCartResponse resp = cartService.addToCart(USER_ID, new AddToCartRequest(BOOK_ID, 3));

        assertEquals(4, resp.getCartItem().getQuantity());
        assertEquals(1L, resp.getCartItemCount());
    }

    @Test
    void addToCart_exceedsMaxItems_throws() {
        UUID book2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        CartEntity cart = cartWithItem(BOOK_ID, 1);
        cart.getItems().add(CartItemEntity.builder()
                .itemId(UUID.randomUUID())
                .cart(cart)
                .bookId(book2)
                .quantity(1)
                .bookPrice(10.0)
                .addedAt(Instant.now())
                .build());
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        stubBook(UUID.fromString("44444444-4444-4444-4444-444444444444"), 5, new BigDecimal("10000"));

        assertThrows(IllegalArgumentException.class, () ->
                cartService.addToCart(USER_ID, new AddToCartRequest(
                        UUID.fromString("44444444-4444-4444-4444-444444444444"), 1)));
    }

    @Test
    void addToCart_insufficientStock_throws() {
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        stubBook(BOOK_ID, 1, new BigDecimal("50000"));

        assertThrows(BookOutOfStockException.class, () ->
                cartService.addToCart(USER_ID, new AddToCartRequest(BOOK_ID, 5)));
    }

    @Test
    void updateCartItem_happy_updatesQuantity() {
        CartEntity cart = cartWithItem(BOOK_ID, 1);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        stubBook(BOOK_ID, 10, new BigDecimal("100000"));

        UpdateCartResponse resp = cartService.updateCartItem(
                USER_ID, BOOK_ID, new UpdateCartItemRequest(3));

        assertEquals(3, resp.getCartItem().getQuantity());
        assertEquals(300000.0, resp.getTotalPrice());
    }

    @Test
    void updateCartItem_itemNotFound_throws() {
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(persistedEmptyCart()));

        assertThrows(ResourceNotFoundException.class, () ->
                cartService.updateCartItem(USER_ID, BOOK_ID, new UpdateCartItemRequest(1)));
    }

    @Test
    void removeFromCart_happy() {
        CartEntity cart = cartWithItem(BOOK_ID, 2);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RemoveCartResponse resp = cartService.removeFromCart(USER_ID, BOOK_ID);

        assertEquals(0L, resp.getCartItemCount());
        assertEquals(0.0, resp.getTotalPrice());
    }

    @Test
    void removeFromCart_itemNotFound_throws() {
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(persistedEmptyCart()));

        assertThrows(ResourceNotFoundException.class, () ->
                cartService.removeFromCart(USER_ID, BOOK_ID));
    }

    @Test
    void clearCart_deletesDbAndCache() {
        cartService.clearCart(USER_ID);

        verify(cartRepository).deleteByUserId(USER_ID);
        verify(redisTemplate).delete(cartKey());
    }

    @Test
    void getSnapshot_enrichesFromFeign() {
        CartEntity cart = cartWithItem(BOOK_ID, 2);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        stubBook(BOOK_ID, 10, new BigDecimal("50000"));

        CartSnapshotResponse snapshot = cartService.getSnapshot(USER_ID);

        assertEquals(USER_ID, snapshot.getUserId());
        assertEquals(1, snapshot.getItems().size());
        assertEquals(0, new BigDecimal("100000").compareTo(snapshot.getTotalPrice()));
    }

    @Test
    void getSnapshot_feignError_usesCachedBookData() {
        CartEntity cart = cartWithItem(BOOK_ID, 2);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(bookServiceClient.getBook(BOOK_ID)).thenThrow(feignNotFound());

        CartSnapshotResponse snapshot = cartService.getSnapshot(USER_ID);

        assertEquals(1, snapshot.getItems().size());
        assertEquals(0, new BigDecimal("200000").compareTo(snapshot.getTotalPrice()));
    }

    @Test
    void getCartItemCount_returnsLineCount() {
        CartEntity cart = cartWithItem(BOOK_ID, 1);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertEquals(1L, cartService.getCartItemCount(USER_ID));
    }

    @Test
    void isBookInCart_trueAndFalse() {
        CartEntity cart = cartWithItem(BOOK_ID, 1);
        when(valueOps.get(cartKey())).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertTrue(cartService.isBookInCart(USER_ID, BOOK_ID));
        assertFalse(cartService.isBookInCart(USER_ID,
                UUID.fromString("99999999-9999-9999-9999-999999999999")));
    }

    private String cartKey() {
        return "cart:" + USER_ID;
    }

    private CartCache emptyCache() {
        Instant now = Instant.now();
        return CartCache.builder()
                .cartId(CART_ID)
                .userId(USER_ID)
                .items(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private CartEntity persistedEmptyCart() {
        Instant now = Instant.now();
        return CartEntity.builder()
                .cartId(CART_ID)
                .userId(USER_ID)
                .items(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private CartEntity cartWithItem(UUID bookId, int qty) {
        CartEntity cart = persistedEmptyCart();
        CartItemEntity item = CartItemEntity.builder()
                .itemId(UUID.randomUUID())
                .bookId(bookId)
                .quantity(qty)
                .bookTitle("Sách")
                .bookPrice(100000.0)
                .addedAt(Instant.now())
                .build();
        item.setCart(cart);
        cart.getItems().add(item);
        return cart;
    }

    private void stubBook(UUID bookId, int stock, BigDecimal price) {
        BookResponse book = BookResponse.builder()
                .id(bookId)
                .title("Sách Test")
                .price(price)
                .stock(stock)
                .build();
        BookApiResponse api = new BookApiResponse();
        api.setCode(200);
        api.setResult(book);
        when(bookServiceClient.getBook(bookId)).thenReturn(api);
    }

    private FeignException.NotFound feignNotFound() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/books/" + BOOK_ID,
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return new FeignException.NotFound("not found", request, null, null);
    }
}
