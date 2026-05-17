package com.notfound.cartservice.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartResponse {

    private CartItemResponse cartItem;
    private Double totalPrice;
}
