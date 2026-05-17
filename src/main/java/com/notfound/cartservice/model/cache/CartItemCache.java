package com.notfound.cartservice.model.cache;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemCache implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID itemId;
    private UUID bookId;
    private Integer quantity;
    private String bookTitle;
    private String bookIsbn;
    private Double bookPrice;
    private Double bookDiscountPrice;
    private String bookImageUrl;
    private Integer stockQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant addedAt;

    public double getSubTotal() {
        if (bookPrice == null || quantity == null) {
            return 0.0;
        }
        return bookPrice * quantity;
    }
}
