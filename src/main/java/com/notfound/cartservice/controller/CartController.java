package com.notfound.cartservice.controller;

import com.notfound.cartservice.model.dto.request.AddCartItemRequest;
import com.notfound.cartservice.model.dto.request.UpdateCartItemRequest;
import com.notfound.cartservice.model.dto.response.ApiResponse;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.dto.response.CartSnapshotResponse;
import com.notfound.cartservice.service.CartService;
import com.notfound.cartservice.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;
    private final UserContext userContext;

    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Lấy giỏ hàng thành công", cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Đã thêm sản phẩm vào giỏ hàng",
                cartService.addItem(userId, request));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartResponse> updateItem(@PathVariable String itemId,
                                                 @Valid @RequestBody UpdateCartItemRequest request) {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Đã cập nhật số lượng",
                cartService.updateItem(userId, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> removeItem(@PathVariable String itemId) {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Đã xoá sản phẩm khỏi giỏ hàng",
                cartService.removeItem(userId, itemId));
    }

    @DeleteMapping
    public ApiResponse<Void> clearCart() {
        UUID userId = userContext.requireUserId();
        cartService.clearCart(userId);
        return ApiResponse.success("Đã xoá toàn bộ giỏ hàng", null);
    }

    @GetMapping("/snapshot")
    public ApiResponse<CartSnapshotResponse> getSnapshot() {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Snapshot giỏ hàng cho checkout",
                cartService.getSnapshot(userId));
    }

    @GetMapping("/count")
    public ApiResponse<Integer> getItemCount() {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Lấy số lượng items thành công",
                cartService.countItems(userId));
    }

    @GetMapping("/check/{bookId}")
    public ApiResponse<Boolean> checkBookInCart(@PathVariable UUID bookId) {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Kiểm tra sách trong giỏ hàng",
                cartService.isBookInCart(userId, bookId));
    }
}
