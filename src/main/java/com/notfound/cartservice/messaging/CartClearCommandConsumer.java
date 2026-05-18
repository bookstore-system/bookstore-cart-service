package com.notfound.cartservice.messaging;

import com.notfound.cartservice.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ConnectionFactory.class)
public class CartClearCommandConsumer {

    private final CartService cartService;
    private final CartEventPublisher cartEventPublisher;
    private final SagaProperties sagaProperties;

    @RabbitListener(queues = "${saga.queue-cart-commands}")
    public void onCartClearCommand(SagaMessage message) {
        UUID userId = requireUserId(message);
        log.info("Received {} sagaId={} userId={} eventId={}",
                sagaProperties.getRkCartClearCommand(), message.getSagaId(), userId, message.getEventId());

        cartService.clearCart(userId);
        cartEventPublisher.publishCartCleared(message);

        log.info("Handled {} sagaId={} userId={}",
                sagaProperties.getRkCartClearCommand(), message.getSagaId(), userId);
    }

    private static UUID requireUserId(SagaMessage message) {
        if (message == null || message.getUserId() == null) {
            throw new IllegalArgumentException("userId is required for cart.clear.command");
        }
        return message.getUserId();
    }
}
