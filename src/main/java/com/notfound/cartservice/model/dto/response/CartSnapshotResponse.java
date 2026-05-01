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
public class CartSnapshotResponse {

    private UUID userId;
    private List<SnapshotItem> items;
    private BigDecimal totalPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant snapshotAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotItem {
        private UUID bookId;
        private String bookTitle;
        private BigDecimal bookPrice;
        private String bookCoverUrl;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}
