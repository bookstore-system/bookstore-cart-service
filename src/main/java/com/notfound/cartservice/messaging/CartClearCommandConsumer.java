package com.notfound.cartservice.messaging;

import com.notfound.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "saga", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CartClearCommandConsumer {

    private final CartService cartService;
    private final CartEventPublisher cartEventPublisher;
    private final SagaProperties sagaProperties;
    private final ObjectMapper objectMapper;

    public CartClearCommandConsumer(CartService cartService,
                                    CartEventPublisher cartEventPublisher,
                                    SagaProperties sagaProperties,
                                    @Qualifier("sagaObjectMapper") ObjectMapper objectMapper) {
        this.cartService = cartService;
        this.cartEventPublisher = cartEventPublisher;
        this.sagaProperties = sagaProperties;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${saga.queue-cart-commands}")
    public void onCartClearCommand(Message rabbitMessage) {
        SagaMessage message;
        try {
            message = objectMapper.readValue(rabbitMessage.getBody(), SagaMessage.class);
        } catch (Exception e) {
            log.error(
                    "Cannot deserialize cart command routingKey={} body={}: {}",
                    rabbitMessage.getMessageProperties().getReceivedRoutingKey(),
                    new String(rabbitMessage.getBody(), StandardCharsets.UTF_8),
                    e.getMessage());
            return;
        }
        UUID userId = requireUserId(message);
        log.info("Received {} sagaId={} userId={} eventId={}",
                sagaProperties.getRkCartClearCommand(), message.getSagaId(), userId, message.getEventId());

        List<UUID> bookIds = extractBookIds(message);
        if (bookIds.isEmpty()) {
            cartService.clearCart(userId);
        } else {
            cartService.clearCartItems(userId, bookIds);
        }
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

    private static List<UUID> extractBookIds(SagaMessage message) {
        if (message == null) {
            return List.of();
        }
        List<UUID> topLevelBookIds = toUuidList(message.getBookIds());
        if (!topLevelBookIds.isEmpty()) {
            return topLevelBookIds;
        }
        if (message.getPayload() == null) {
            return List.of();
        }
        Object value = message.getPayload().get("bookIds");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return toUuidList(list);
    }

    private static List<UUID> toUuidList(List<?> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }
        return bookIds.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(bookId -> !bookId.isBlank())
                .distinct()
                .map(UUID::fromString)
                .toList();
    }
}
