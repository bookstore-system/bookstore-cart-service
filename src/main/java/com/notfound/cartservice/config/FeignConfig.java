package com.notfound.cartservice.config;

import com.notfound.cartservice.exception.ResourceNotFoundException;
import com.notfound.cartservice.exception.ServiceUnavailableException;
import feign.Logger;
import feign.Request;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class FeignConfig {

    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(
                3, TimeUnit.SECONDS,
                5, TimeUnit.SECONDS,
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new CartFeignErrorDecoder();
    }

    static class CartFeignErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, Response response) {
            log.warn("Feign call failed: methodKey={}, status={}", methodKey, response.status());
            int status = response.status();
            if (status == 404) {
                return new ResourceNotFoundException("Không tìm thấy resource từ service phụ thuộc");
            }
            if (status >= 500 || status == 503) {
                return new ServiceUnavailableException("book-service");
            }
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
