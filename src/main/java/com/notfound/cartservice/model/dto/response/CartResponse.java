package com.notfound.cartservice.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private UUID cartId;
    private UUID userId;
    private List<CartItemResponse> items;
    private Long itemCount;
    private Double totalPrice;
}
