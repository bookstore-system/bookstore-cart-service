package com.notfound.cartservice.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartResponse {

    private CartItemResponse cartItem;
    private Long cartItemCount;
}
