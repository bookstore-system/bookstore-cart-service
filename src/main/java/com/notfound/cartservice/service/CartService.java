package com.notfound.cartservice.service;

import com.notfound.cartservice.model.dto.request.AddCartItemRequest;
import com.notfound.cartservice.model.dto.request.UpdateCartItemRequest;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.dto.response.CartSnapshotResponse;

import java.util.UUID;

public interface CartService {

    CartResponse getCart(UUID userId);

    CartResponse addItem(UUID userId, AddCartItemRequest request);

    CartResponse updateItem(UUID userId, String itemId, UpdateCartItemRequest request);

    CartResponse removeItem(UUID userId, String itemId);

    void clearCart(UUID userId);

    CartSnapshotResponse getSnapshot(UUID userId);

    int countItems(UUID userId);

    boolean isBookInCart(UUID userId, UUID bookId);
}
