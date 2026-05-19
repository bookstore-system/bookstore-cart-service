package com.notfound.cartservice.messaging;

import com.notfound.cartservice.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
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

    @InjectMocks
    private CartClearCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        when(sagaProperties.getRkCartClearCommand()).thenReturn("cart.clear.command");
    }

    @Test
    void onCartClearCommand_clearsCartAndPublishesEvent() {
        SagaMessage command = sampleCommand();

        consumer.onCartClearCommand(command);

        verify(cartService).clearCart(USER_ID);
        verify(cartEventPublisher).publishCartCleared(command);
    }

    @Test
    void onCartClearCommand_duplicateCommand_isIdempotent() {
        SagaMessage command = sampleCommand();

        consumer.onCartClearCommand(command);
        consumer.onCartClearCommand(command);

        verify(cartService, times(2)).clearCart(USER_ID);
        verify(cartEventPublisher, times(2)).publishCartCleared(command);
    }

    @Test
    void onCartClearCommand_emptyCart_stillPublishesCleared() {
        SagaMessage command = sampleCommand();

        consumer.onCartClearCommand(command);

        verify(cartService).clearCart(USER_ID);
        verify(cartEventPublisher).publishCartCleared(command);
    }

    @Test
    void onCartClearCommand_missingUserId_throwsAndDoesNotPublish() {
        SagaMessage command = sampleCommand();
        command.setUserId(null);

        assertThrows(IllegalArgumentException.class, () -> consumer.onCartClearCommand(command));

        verifyNoInteractions(cartService);
        verifyNoInteractions(cartEventPublisher);
    }

    private SagaMessage sampleCommand() {
        return SagaMessage.builder()
                .eventId(EVENT_ID)
                .sagaId(SAGA_ID)
                .correlationId(CORRELATION_ID)
                .type("cart.clear.command")
                .occurredAt(Instant.parse("2026-05-18T12:00:00Z"))
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .build();
    }
}
