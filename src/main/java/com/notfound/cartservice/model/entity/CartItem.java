package com.notfound.cartservice.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;

    private UUID bookId;

    private Integer quantity;

    private String bookTitle;

    private BigDecimal bookPrice;

    private String bookCoverUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant addedAt;

    public BigDecimal getSubtotal() {
        if (bookPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return bookPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
