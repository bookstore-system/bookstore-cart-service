package com.notfound.cartservice.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String serviceName) {
        super("Dịch vụ '" + serviceName + "' tạm thời không khả dụng. Vui lòng thử lại sau.");
    }
}
