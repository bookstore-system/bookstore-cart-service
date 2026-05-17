package com.notfound.cartservice.mapper;

import com.notfound.cartservice.model.cache.CartCache;
import com.notfound.cartservice.model.cache.CartItemCache;
import com.notfound.cartservice.model.entity.CartEntity;
import com.notfound.cartservice.model.entity.CartItemEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CartCacheMapper {

    public CartCache toCache(CartEntity entity) {
        if (entity == null) {
            return null;
        }
        List<CartItemCache> items = entity.getItems() == null
                ? new ArrayList<>()
                : entity.getItems().stream().map(this::toItemCache).collect(Collectors.toList());

        return CartCache.builder()
                .cartId(entity.getCartId())
                .userId(entity.getUserId())
                .items(items)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public CartEntity toEntity(CartCache cache) {
        if (cache == null) {
            return null;
        }
        CartEntity entity = CartEntity.builder()
                .cartId(cache.getCartId())
                .userId(cache.getUserId())
                .createdAt(cache.getCreatedAt())
                .updatedAt(cache.getUpdatedAt())
                .items(new ArrayList<>())
                .build();

        if (cache.getItems() != null) {
            for (CartItemCache itemCache : cache.getItems()) {
                CartItemEntity item = toItemEntity(itemCache, entity);
                entity.getItems().add(item);
            }
        }
        return entity;
    }

    private CartItemCache toItemCache(CartItemEntity item) {
        return CartItemCache.builder()
                .itemId(item.getItemId())
                .bookId(item.getBookId())
                .quantity(item.getQuantity())
                .bookTitle(item.getBookTitle())
                .bookIsbn(item.getBookIsbn())
                .bookPrice(item.getBookPrice())
                .bookDiscountPrice(item.getBookDiscountPrice())
                .bookImageUrl(item.getBookImageUrl())
                .stockQuantity(item.getStockQuantity())
                .addedAt(item.getAddedAt())
                .build();
    }

    private CartItemEntity toItemEntity(CartItemCache cache, CartEntity cart) {
        CartItemEntity item = CartItemEntity.builder()
                .itemId(cache.getItemId())
                .bookId(cache.getBookId())
                .quantity(cache.getQuantity())
                .bookTitle(cache.getBookTitle())
                .bookIsbn(cache.getBookIsbn())
                .bookPrice(cache.getBookPrice())
                .bookDiscountPrice(cache.getBookDiscountPrice())
                .bookImageUrl(cache.getBookImageUrl())
                .stockQuantity(cache.getStockQuantity())
                .addedAt(cache.getAddedAt())
                .build();
        item.setCart(cart);
        return item;
    }
}
