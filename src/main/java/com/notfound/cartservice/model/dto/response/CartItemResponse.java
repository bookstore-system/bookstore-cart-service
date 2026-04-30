package com.notfound.cartservice.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private String itemId;
    private UUID bookId;
    private String bookTitle;
    private BigDecimal bookPrice;
    private String bookCoverUrl;
    private Integer quantity;
    private BigDecimal subtotal;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant addedAt;
}
