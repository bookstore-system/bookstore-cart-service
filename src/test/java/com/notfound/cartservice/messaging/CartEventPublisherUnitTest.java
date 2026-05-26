package com.notfound.cartservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartEventPublisherUnitTest {

    private static final String EVENTS_EXCHANGE = "bookstore.events";
    private static final String CART_CLEARED_RK = "cart.cleared";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private SagaProperties sagaProperties;

    @InjectMocks
    private CartEventPublisher publisher;

    @BeforeEach
    void setUp() {
        when(sagaProperties.getExchangeEvents()).thenReturn(EVENTS_EXCHANGE);
        when(sagaProperties.getRkCartCleared()).thenReturn(CART_CLEARED_RK);
    }

    @Test
    void publishCartCleared_sendsToEventsExchangeWithCorrectEnvelope() {
        UUID incomingEventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID sagaId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID correlationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID orderId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        SagaMessage incoming = SagaMessage.builder()
                .eventId(incomingEventId)
                .sagaId(sagaId)
                .correlationId(correlationId)
                .type("cart.clear.command")
                .occurredAt(LocalDateTime.parse("2026-05-18T12:00:00"))
                .orderId(orderId)
                .userId(userId)
                .build();

        publisher.publishCartCleared(incoming);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SagaMessage> messageCaptor = ArgumentCaptor.forClass(SagaMessage.class);

        verify(rabbitTemplate).convertAndSend(
                exchangeCaptor.capture(),
                routingKeyCaptor.capture(),
                messageCaptor.capture(),
                org.mockito.ArgumentMatchers.any(MessagePostProcessor.class));

        assertEquals(EVENTS_EXCHANGE, exchangeCaptor.getValue());
        assertEquals(CART_CLEARED_RK, routingKeyCaptor.getValue());

        SagaMessage published = messageCaptor.getValue();
        assertNotNull(published.getEventId());
        assertEquals(sagaId, published.getSagaId());
        assertEquals(correlationId, published.getCorrelationId());
        assertEquals(incomingEventId, published.getCausationId());
        assertEquals(CART_CLEARED_RK, published.getType());
        assertNotNull(published.getOccurredAt());
        assertEquals(orderId, published.getOrderId());
        assertEquals(userId, published.getUserId());
    }
}
