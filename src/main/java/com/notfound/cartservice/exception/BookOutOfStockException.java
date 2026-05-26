package com.notfound.cartservice.exception;

import java.util.UUID;

public class BookOutOfStockException extends RuntimeException {

    public BookOutOfStockException(String message) {
        super(message);
    }

    public BookOutOfStockException(UUID bookId, int available) {
        super("Sách (id=" + bookId + ") không đủ tồn kho. Chỉ còn " + available + " quyển");
    }
}
