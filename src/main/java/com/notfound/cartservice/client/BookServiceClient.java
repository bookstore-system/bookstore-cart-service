package com.notfound.cartservice.client;

import com.notfound.cartservice.client.dto.BookApiResponse;
import com.notfound.cartservice.client.dto.BatchBookRequest;
import com.notfound.cartservice.client.dto.BatchBookResponse;
import com.notfound.cartservice.client.dto.BookServiceApiResponse;
import com.notfound.cartservice.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "book-service",
        url = "${book-service.url}",
        configuration = FeignConfig.class
)
public interface BookServiceClient {

    @GetMapping("/api/v1/books/{id}")
    BookApiResponse getBook(@PathVariable("id") UUID id);

    @PostMapping("/api/v1/books/batch")
    BookServiceApiResponse<BatchBookResponse> getBooksBatch(@RequestBody BatchBookRequest request);
}
