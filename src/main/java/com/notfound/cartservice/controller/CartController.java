package com.notfound.cartservice.controller;

import com.notfound.cartservice.model.dto.request.AddToCartRequest;
import com.notfound.cartservice.model.dto.request.UpdateCartItemRequest;
import com.notfound.cartservice.model.dto.response.AddToCartResponse;
import com.notfound.cartservice.model.dto.response.ApiResponse;
import com.notfound.cartservice.model.dto.response.CartResponse;
import com.notfound.cartservice.model.dto.response.CartSnapshotResponse;
import com.notfound.cartservice.model.dto.response.RemoveCartResponse;
import com.notfound.cartservice.model.dto.response.UpdateCartResponse;
import com.notfound.cartservice.service.CartService;
import com.notfound.cartservice.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Giỏ hàng", description = "API giỏ hàng tương thích monolith (/api/cart + v1); header `X-User-Id` + `X-User-Role`")
public class CartController {

    private final CartService cartService;
    private final UserContext userContext;

    @GetMapping
    @Operation(summary = "Lấy giỏ hàng hiện tại")
    public ApiResponse<CartResponse> getCart() {
        UUID userId = userContext.requireUserId();
        CartResponse cart = cartService.getCart(userId);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            CartResponse empty = CartResponse.builder()
                    .cartId(cart.getCartId())
                    .userId(userId)
                    .items(List.of())
                    .itemCount(0L)
                    .totalPrice(0.0)
                    .build();
            return ApiResponse.success("Không có sản phẩm trong giỏ hàng", empty);
        }

        return ApiResponse.success("Lấy giỏ hàng thành công", cart);
    }

    @PostMapping("/add")
    @Operation(summary = "Thêm sách vào giỏ")
    public ApiResponse<AddToCartResponse> addToCart(@Valid @RequestBody AddToCartRequest request) {
        UUID userId = userContext.requireUserId();
        AddToCartResponse response = cartService.addToCart(userId, request);
        return ApiResponse.success("Đã thêm sản phẩm vào giỏ hàng", response);
    }

    @PutMapping("/update/{bookId}")
    @Operation(summary = "Cập nhật số lượng theo bookId")
    public ApiResponse<UpdateCartResponse> updateCartItem(
            @PathVariable UUID bookId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        UUID userId = userContext.requireUserId();
        UpdateCartResponse response = cartService.updateCartItem(userId, bookId, request);
        return ApiResponse.success("Đã cập nhật số lượng", response);
    }

    @DeleteMapping("/remove/{bookId}")
    @Operation(summary = "Xóa sách khỏi giỏ theo bookId")
    public ApiResponse<RemoveCartResponse> removeFromCart(@PathVariable UUID bookId) {
        UUID userId = userContext.requireUserId();
        RemoveCartResponse response = cartService.removeFromCart(userId, bookId);

        String message = response.getCartItemCount() == 0
                ? "Đã xóa sản phẩm. Giỏ hàng hiện đang trống"
                : "Đã xóa sản phẩm khỏi giỏ hàng";

        return ApiResponse.success(message, response);
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Xóa toàn bộ giỏ")
    public ApiResponse<Void> clearCart() {
        UUID userId = userContext.requireUserId();

        if (cartService.isCartEmpty(userId)) {
            return ApiResponse.success("Giỏ hàng đã trống", null);
        }

        cartService.clearCart(userId);
        return ApiResponse.success("Đã xóa toàn bộ giỏ hàng", null);
    }

    @GetMapping("/snapshot")
    @Operation(summary = "Snapshot giỏ cho order-service (nội bộ)")
    public ApiResponse<CartSnapshotResponse> getSnapshot() {
        UUID userId = userContext.requireUserId();
        return ApiResponse.success("Snapshot giỏ hàng cho checkout",
                cartService.getSnapshot(userId));
    }

    @GetMapping("/count")
    @Operation(summary = "Số dòng sản phẩm trong giỏ")
    public ApiResponse<Long> getCartItemCount() {
        UUID userId = userContext.requireUserId();
        Long count = cartService.getCartItemCount(userId);
        return ApiResponse.success("Lấy số lượng items thành công", count);
    }

    @GetMapping("/check/{bookId}")
    @Operation(summary = "Kiểm tra sách đã có trong giỏ chưa")
    public ApiResponse<Boolean> checkBookInCart(@PathVariable UUID bookId) {
        UUID userId = userContext.requireUserId();
        boolean inCart = cartService.isBookInCart(userId, bookId);
        return ApiResponse.success("Kiểm tra sách trong giỏ hàng thành công", inCart);
    }
}
