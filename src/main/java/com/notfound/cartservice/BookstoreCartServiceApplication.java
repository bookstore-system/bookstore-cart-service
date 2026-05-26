package com.notfound.cartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class BookstoreCartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookstoreCartServiceApplication.class, args);
    }
}
