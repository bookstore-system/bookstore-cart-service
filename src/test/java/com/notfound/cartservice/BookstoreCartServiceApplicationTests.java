package com.notfound.cartservice;

import com.notfound.cartservice.client.BookServiceClient;
import com.notfound.cartservice.model.cache.CartCache;
import com.notfound.cartservice.repository.CartItemRepository;
import com.notfound.cartservice.repository.CartRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class BookstoreCartServiceApplicationTests {

    @MockitoBean
    CartRepository cartRepository;

    @MockitoBean
    CartItemRepository cartItemRepository;

    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    RedisTemplate<String, CartCache> redisTemplate;

    @MockitoBean
    BookServiceClient bookServiceClient;

    @Test
    void contextLoads() {
    }
}
