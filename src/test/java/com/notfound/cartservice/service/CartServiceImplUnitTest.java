package com.notfound.cartservice.service;

import com.notfound.cartservice.client.BookServiceClient;
import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.client.dto.BookResponse;
import com.notfound.cartservice.model.dto.request.AddCartItemRequest;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.entity.Cart;
import com.notfound.cartservice.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class CartServiceImplUnitTest {

    @Mock
    RedisTemplate<String, Cart> redisTemplate;

    @Mock
    ValueOperations<String, Cart> valueOps;

    @Mock
    BookServiceClient bookServiceClient;

    @InjectMocks
    CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cartService, "ttlDays", 7L);
        ReflectionTestUtils.setField(cartService, "maxItemsPerCart", 100);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getCart_returnsEmptyCartWhenNotInRedis() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Mockito.when(valueOps.get("cart:" + userId)).thenReturn(null);

        CartResponse resp = cartService.getCart(userId);

        assertNotNull(resp);
        assertEquals(userId, resp.getUserId());
        assertEquals(0, resp.getItemCount());
    }

    @Test
    void addItem_addsItemAndPersists() {
        Mockito.when(valueOps.get(anyString())).thenReturn(null);

        UUID bookId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        BookResponse book = BookResponse.builder()
                .id(bookId)
                .title("Sách Test")
                .price(new BigDecimal("100000"))
                .stock(5)
                .build();
        BookApiResponse api = new BookApiResponse();
        api.setCode(200);
        api.setResult(book);
        Mockito.when(bookServiceClient.getBook(bookId)).thenReturn(api);

        AddCartItemRequest req = AddCartItemRequest.builder()
                .bookId(bookId).quantity(2).build();

        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CartResponse resp = cartService.addItem(userId, req);

        assertEquals(1, resp.getItems().size());
        assertEquals(bookId, resp.getItems().get(0).getBookId());
        assertEquals(2, resp.getItems().get(0).getQuantity());
        Mockito.verify(valueOps).set(any(), any(), any(java.time.Duration.class));
    }
}
