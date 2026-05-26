package com.notfound.cartservice.model.cache;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartCache implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID cartId;
    private UUID userId;

    @Builder.Default
    private List<CartItemCache> items = new ArrayList<>();

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public long lineItemCount() {
        return items == null ? 0L : items.size();
    }

    public double totalPrice() {
        if (items == null || items.isEmpty()) {
            return 0.0;
        }
        return items.stream().mapToDouble(CartItemCache::getSubTotal).sum();
    }
}
