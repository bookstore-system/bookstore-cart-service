package com.notfound.cartservice.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public BigDecimal totalPrice() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int totalItems() {
        if (items == null) return 0;
        return items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
    }
}
