package com.posgateway.aml.dto.market;

import java.time.LocalDateTime;
import java.util.Map;

public final class FixSurveillanceDtos {
    private FixSurveillanceDtos() {}

    public record FixSessionResponse(
            String sessionId,
            Long pspId,
            String connectionType,
            boolean enabled,
            boolean loggedOn,
            Integer expectedSenderSequence,
            Integer expectedTargetSequence) {}

    public record FixMessageEventResponse(
            Long id,
            String sessionId,
            String direction,
            String messageType,
            int messageSequenceNumber,
            LocalDateTime sendingTime,
            String businessReference,
            String messageHash,
            Map<String, Object> sanitizedFields,
            String outcome,
            String errorCode,
            String errorMessage,
            Long marketOrderId,
            Long marketExecutionId,
            LocalDateTime receivedAt,
            LocalDateTime processedAt) {}
}
