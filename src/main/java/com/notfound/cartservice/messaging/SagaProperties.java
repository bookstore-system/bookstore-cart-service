package com.notfound.cartservice.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "saga")
public class SagaProperties {

    private String exchangeCommands = "bookstore.commands";
    private String exchangeEvents = "bookstore.events";
    private String queueCartCommands = "cart.commands.queue";
    private String rkCartClearCommand = "cart.clear.command";
    private String rkCartCleared = "cart.cleared";
}
