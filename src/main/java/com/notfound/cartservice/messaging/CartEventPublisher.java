package com.notfound.cartservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "saga", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CartEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final SagaProperties sagaProperties;

    public void publishCartCleared(SagaMessage incoming) {
        SagaMessage event = SagaMessage.builder()
                .eventId(UUID.randomUUID())
                .sagaId(incoming.getSagaId())
                .correlationId(incoming.getCorrelationId())
                .causationId(incoming.getEventId())
                .type(sagaProperties.getRkCartCleared())
                .occurredAt(Instant.now())
                .orderId(incoming.getOrderId())
                .userId(incoming.getUserId())
                .payload(incoming.getPayload())
                .build();

        rabbitTemplate.convertAndSend(
                sagaProperties.getExchangeEvents(),
                sagaProperties.getRkCartCleared(),
                event);

        log.info("Published cart.cleared sagaId={} userId={} eventId={}",
                event.getSagaId(), event.getUserId(), event.getEventId());
    }
}
