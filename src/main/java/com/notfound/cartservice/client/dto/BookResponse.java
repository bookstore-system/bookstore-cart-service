package com.notfound.cartservice.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookResponse {

    private UUID id;

    private String title;

    private BigDecimal price;

    @JsonAlias({"originalPrice", "original_price"})
    private BigDecimal originalPrice;

    @JsonAlias({"stock", "stockQuantity", "stock_quantity"})
    private Integer stock;

    @JsonAlias({"coverUrl", "cover_url", "imageUrl"})
    private String coverUrl;

    private Boolean active;
}
