package com.notfound.cartservice.client;

import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "book-service",
        url = "${book-service.url}",
        configuration = FeignConfig.class
)
public interface BookServiceClient {

    @GetMapping("/api/books/{id}")
    BookApiResponse getBook(@PathVariable("id") UUID id);
}
