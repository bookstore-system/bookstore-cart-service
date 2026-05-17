package com.notfound.cartservice.mapper;

import com.notfound.cartservice.model.dto.response.CartItemResponse;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.entity.CartEntity;
import com.notfound.cartservice.model.entity.CartItemEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CartMapper {

    public CartItemResponse toCartItemResponse(CartItemEntity item) {
        return CartItemResponse.builder()
                .itemId(item.getItemId())
                .bookId(item.getBookId())
                .bookTitle(item.getBookTitle())
                .bookIsbn(item.getBookIsbn())
                .bookPrice(item.getBookPrice())
                .bookDiscountPrice(item.getBookDiscountPrice())
                .bookImageUrl(item.getBookImageUrl())
                .quantity(item.getQuantity())
                .subTotal(item.getSubTotal())
                .stockQuantity(item.getStockQuantity())
                .build();
    }

    public CartResponse toCartResponse(CartEntity cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::toCartItemResponse)
                .toList();

        return CartResponse.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .itemCount(cart.lineItemCount())
                .totalPrice(cart.totalPrice())
                .build();
    }
}
