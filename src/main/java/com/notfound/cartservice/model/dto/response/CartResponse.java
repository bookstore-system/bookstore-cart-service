package com.notfound.cartservice.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private UUID userId;
    private List<CartItemResponse> items;
    private Integer itemCount;
    private BigDecimal totalPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
}
