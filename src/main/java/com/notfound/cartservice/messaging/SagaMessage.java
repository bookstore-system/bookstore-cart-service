package com.notfound.cartservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope chung cho command/event checkout saga (theo ai-agent-saga/context/api.md).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SagaMessage {

    private UUID eventId;
    private UUID sagaId;
    private UUID correlationId;
    private UUID causationId;
    private String type;
    private Instant occurredAt;
    private UUID orderId;
    private UUID userId;
    private Map<String, Object> payload;
}
