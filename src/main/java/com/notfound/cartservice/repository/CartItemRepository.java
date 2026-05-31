package com.notfound.cartservice.repository;

import com.notfound.cartservice.model.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    @Modifying
    @Transactional
    @Query("""
            update CartItemEntity item
            set item.quantity = :quantity,
                item.bookTitle = :bookTitle,
                item.bookIsbn = :bookIsbn,
                item.bookPrice = :bookPrice,
                item.bookDiscountPrice = :bookDiscountPrice,
                item.bookImageUrl = :bookImageUrl,
                item.stockQuantity = :stockQuantity
            where item.itemId = :itemId
            """)
    int updateItemDetails(
            @Param("itemId") UUID itemId,
            @Param("quantity") Integer quantity,
            @Param("bookTitle") String bookTitle,
            @Param("bookIsbn") String bookIsbn,
            @Param("bookPrice") Double bookPrice,
            @Param("bookDiscountPrice") Double bookDiscountPrice,
            @Param("bookImageUrl") String bookImageUrl,
            @Param("stockQuantity") Integer stockQuantity
    );

    @Modifying
    @Transactional
    @Query("""
            delete from CartItemEntity item
            where item.cart.cartId = :cartId and item.bookId = :bookId
            """)
    int deleteByCartIdAndBookId(@Param("cartId") UUID cartId, @Param("bookId") UUID bookId);
}
