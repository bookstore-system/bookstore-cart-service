package com.notfound.cartservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchBookResponse {
    private List<BookItem> items;
    private List<UUID> missingIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookItem {
        private UUID id;
        private String title;
        private BigDecimal price;
        private BigDecimal discountPrice;
        private String thumbnailUrl;
        private Boolean inStock;
    }
}
