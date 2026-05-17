package com.notfound.cartservice.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class BookResponse {

    private UUID id;

    private String title;

    private String isbn;

    private BigDecimal price;

    @JsonAlias({"discountPrice", "discount_price"})
    private BigDecimal discountPrice;

    @JsonAlias({"originalPrice", "original_price"})
    private BigDecimal originalPrice;

    @JsonAlias({"stock", "stockQuantity", "stock_quantity"})
    private Integer stock;

    @JsonAlias({"coverUrl", "cover_url", "imageUrl"})
    private String coverUrl;

    @JsonAlias({"mainImageUrl", "main_image_url"})
    private String mainImageUrl;

    private List<String> imageUrls;

    /** book-service đôi khi trả mảng object `{ "url": "..." }`. */
    private List<BookImageRef> images;

    private Boolean active;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookImageRef {
        private String url;
    }

    /** Ảnh bìa từ book-service (ưu tiên coverUrl → mainImageUrl → imageUrls[0] → images[0].url). */
    public String resolveImageUrl() {
        if (coverUrl != null && !coverUrl.isBlank()) {
            return coverUrl;
        }
        if (mainImageUrl != null && !mainImageUrl.isBlank()) {
            return mainImageUrl;
        }
        if (imageUrls != null && !imageUrls.isEmpty()) {
            String first = imageUrls.get(0);
            if (first != null && !first.isBlank()) {
                return first;
            }
        }
        if (images != null && !images.isEmpty()) {
            for (BookImageRef ref : images) {
                if (ref != null && ref.getUrl() != null && !ref.getUrl().isBlank()) {
                    return ref.getUrl();
                }
            }
        }
        return null;
    }

    public Double getPriceAsDouble() {
        return price == null ? 0.0 : price.doubleValue();
    }

    public Double getDiscountPriceAsDouble() {
        return discountPrice == null ? null : discountPrice.doubleValue();
    }
}
