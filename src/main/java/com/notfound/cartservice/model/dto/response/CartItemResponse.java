package com.notfound.cartservice.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private UUID itemId;
    private UUID bookId;
    private String bookTitle;
    private String bookIsbn;
    private Double bookPrice;
    private Double bookDiscountPrice;
    private String bookImageUrl;
    private Integer quantity;
    private Double subTotal;
    private Integer stockQuantity;
}
