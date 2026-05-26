package com.notfound.cartservice.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_NAME = "X-User-Name";

    private final HttpServletRequest request;

    public UUID requireUserId() {
        String raw = request.getHeader(HEADER_USER_ID);
        if (raw == null || raw.isBlank()) {
            throw new com.notfound.cartservice.exception.UnauthorizedException(
                    "Vui lòng đăng nhập (thiếu header X-User-Id từ Gateway)");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("X-User-Id không hợp lệ: {}", raw);
            throw new com.notfound.cartservice.exception.UnauthorizedException(
                    "X-User-Id không hợp lệ");
        }
    }

    public String getUserRole() {
        String role = request.getHeader(HEADER_USER_ROLE);
        return role == null ? "ROLE_USER" : role.trim();
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equalsIgnoreCase(getUserRole());
    }
}
