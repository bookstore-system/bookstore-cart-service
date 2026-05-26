package com.notfound.cartservice.service;

import com.notfound.cartservice.model.dto.request.AddToCartRequest;
import com.notfound.cartservice.model.dto.request.UpdateCartItemRequest;
import com.notfound.cartservice.model.dto.response.AddToCartResponse;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.dto.response.CartSnapshotResponse;
import com.notfound.cartservice.model.dto.response.RemoveCartResponse;
import com.notfound.cartservice.model.dto.response.UpdateCartResponse;

import java.util.UUID;

public interface CartService {

    CartResponse getCart(UUID userId);

    AddToCartResponse addToCart(UUID userId, AddToCartRequest request);

    UpdateCartResponse updateCartItem(UUID userId, UUID bookId, UpdateCartItemRequest request);

    RemoveCartResponse removeFromCart(UUID userId, UUID bookId);

    void clearCart(UUID userId);

    boolean isCartEmpty(UUID userId);

    CartSnapshotResponse getSnapshot(UUID userId);

    Long getCartItemCount(UUID userId);

    boolean isBookInCart(UUID userId, UUID bookId);
}
