package com.notfound.cartservice.controller;

import com.notfound.cartservice.exception.GlobalExceptionHandler;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.service.CartService;
import com.notfound.cartservice.util.UserContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest(CartController.class)
@Import({GlobalExceptionHandler.class})
class CartControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CartService cartService;

    @MockitoBean
    UserContext userContext;

    @Test
    void getCart_returnsApiResponse() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Mockito.when(userContext.requireUserId()).thenReturn(userId);
        CartResponse mockResp = CartResponse.builder()
                .userId(userId)
                .items(List.of())
                .itemCount(0)
                .totalPrice(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build();
        Mockito.when(cartService.getCart(userId)).thenReturn(mockResp);

        mockMvc.perform(get("/api/cart").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.result.userId").value(userId.toString()));
    }
}
