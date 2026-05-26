package com.notfound.cartservice.messaging;

import com.notfound.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartClearCommandConsumerUnitTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SAGA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CORRELATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private CartService cartService;

    @Mock
    private CartEventPublisher cartEventPublisher;

    @Mock
    private SagaProperties sagaProperties;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CartClearCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        when(sagaProperties.getRkCartClearCommand()).thenReturn("cart.clear.command");
    }

    @Test
    void onCartClearCommand_clearsCartAndPublishesEvent() throws Exception {
        SagaMessage command = sampleCommand();
        Message rabbitMessage = rabbitMessage(command);

        consumer.onCartClearCommand(rabbitMessage);

        verify(cartService).clearCart(USER_ID);
        verify(cartEventPublisher).publishCartCleared(command);
    }

    @Test
    void onCartClearCommand_duplicateCommand_isIdempotent() throws Exception {
        SagaMessage command = sampleCommand();
        Message rabbitMessage = rabbitMessage(command);

        consumer.onCartClearCommand(rabbitMessage);
        consumer.onCartClearCommand(rabbitMessage);

        verify(cartService, times(2)).clearCart(USER_ID);
        verify(cartEventPublisher, times(2)).publishCartCleared(command);
    }

    @Test
    void onCartClearCommand_emptyCart_stillPublishesCleared() throws Exception {
        SagaMessage command = sampleCommand();
        Message rabbitMessage = rabbitMessage(command);

        consumer.onCartClearCommand(rabbitMessage);

        verify(cartService).clearCart(USER_ID);
        verify(cartEventPublisher).publishCartCleared(command);
    }

    @Test
    void onCartClearCommand_missingUserId_throwsAndDoesNotPublish() throws Exception {
        SagaMessage command = sampleCommand();
        command.setUserId(null);
        Message rabbitMessage = rabbitMessage(command);

        assertThrows(IllegalArgumentException.class, () -> consumer.onCartClearCommand(rabbitMessage));

        verifyNoInteractions(cartService);
        verifyNoInteractions(cartEventPublisher);
    }

    private SagaMessage sampleCommand() {
        return SagaMessage.builder()
                .eventId(EVENT_ID)
                .sagaId(SAGA_ID)
                .correlationId(CORRELATION_ID)
                .type("cart.clear.command")
                .occurredAt(LocalDateTime.parse("2026-05-18T12:00:00"))
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .build();
    }

    private Message rabbitMessage(SagaMessage command) throws Exception {
        Message message = new Message(new byte[0], new MessageProperties());
        when(objectMapper.readValue(message.getBody(), SagaMessage.class)).thenReturn(command);
        return message;
    }
}
