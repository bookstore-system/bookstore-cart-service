package com.notfound.cartservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemEntity {

    @Id
    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "book_title")
    private String bookTitle;

    @Column(name = "book_isbn")
    private String bookIsbn;

    @Column(name = "book_price")
    private Double bookPrice;

    @Column(name = "book_discount_price")
    private Double bookDiscountPrice;

    @Column(name = "book_image_url", length = 1000)
    private String bookImageUrl;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "added_at")
    private Instant addedAt;

    public double getSubTotal() {
        if (bookPrice == null || quantity == null) {
            return 0.0;
        }
        return bookPrice * quantity;
    }
}
