package com.notfound.cartservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String HEADER_USER_ID = "userIdHeader";
    public static final String HEADER_USER_ROLE = "userRoleHeader";

    @Bean
    public OpenAPI cartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bookstore Cart Service API")
                        .version("1.0")
                        .description("""
                                API giỏ hàng (`/api/v1/cart`). JWT được xử lý tại **API Gateway**; service tin cậy header \
                                `X-User-Id` (UUID) và khuyến nghị `X-User-Role`. Khi gọi trực tiếp service (dev), dùng nút \
                                **Authorize** trên Swagger UI để nhập header."""))
                .components(new Components()
                        .addSecuritySchemes(HEADER_USER_ID, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("UUID người dùng (Gateway forward)"))
                        .addSecuritySchemes(HEADER_USER_ROLE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("Ví dụ: ROLE_USER")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(HEADER_USER_ID)
                        .addList(HEADER_USER_ROLE));
    }
}
