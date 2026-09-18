package com.posgateway.aml.service.market;

import com.posgateway.aml.dto.market.FixSurveillanceDtos.FixMessageEventResponse;
import com.posgateway.aml.entity.integration.FixMessageEvent;
import com.posgateway.aml.repository.integration.FixMessageEventRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FixMessageEvidenceService {
    private static final List<Integer> STANDARD_EVIDENCE_TAGS = List.of(
            Account.FIELD, AvgPx.FIELD, ClOrdID.FIELD, CumQty.FIELD, quickfix.field.Currency.FIELD, ExecID.FIELD,
            LastPx.FIELD, LastQty.FIELD, OrderID.FIELD, OrderQty.FIELD, OrdStatus.FIELD, OrdType.FIELD,
            OrigClOrdID.FIELD, Price.FIELD, SecurityID.FIELD, Side.FIELD, Symbol.FIELD, Text.FIELD,
            TimeInForce.FIELD, TransactTime.FIELD, ExDestination.FIELD, ExecType.FIELD, LeavesQty.FIELD,
            ContraBroker.FIELD);

    private final FixMessageEventRepository repository;
    private final PspIsolationService isolationService;

    public FixMessageEvidenceService(FixMessageEventRepository repository, PspIsolationService isolationService) {
        this.repository = repository;
        this.isolationService = isolationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long receive(Long pspId, SessionID sessionId, Message message, Collection<Integer> customEvidenceTags)
            throws FieldNotFound {
        int sequence = message.getHeader().getInt(MsgSeqNum.FIELD);
        String incomingHash = hash(message.toString());
        Optional<FixMessageEvent> existing = repository
                .findBySessionIdAndMessageSequenceNumberAndDirection(sessionId.toString(), sequence, "INBOUND");
        if (existing.isPresent()) {
            if (!MessageDigest.isEqual(existing.get().getMessageHash().getBytes(StandardCharsets.UTF_8),
                    incomingHash.getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("FIX replay sequence contains different message content");
            }
            return existing.get().getId();
        }

        FixMessageEvent event = new FixMessageEvent();
        event.setPspId(pspId);
        event.setSessionId(sessionId.toString());
        event.setDirection("INBOUND");
        event.setMessageType(message.getHeader().getString(MsgType.FIELD));
        event.setMessageSequenceNumber(sequence);
        if (message.getHeader().isSetField(SendingTime.FIELD)) {
            event.setSendingTime(message.getHeader().getUtcTimeStamp(SendingTime.FIELD));
        }
        event.setBusinessReference(first(message, ExecID.FIELD, ClOrdID.FIELD, OrigClOrdID.FIELD, OrderID.FIELD));
        event.setMessageHash(incomingHash);
        event.setSanitizedFields(sanitized(message, customEvidenceTags));
        event.setOutcome("RECEIVED");
        return repository.save(event).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accepted(Long eventId, Long marketOrderId, Long marketExecutionId) {
        update(eventId, "ACCEPTED", null, null, marketOrderId, marketExecutionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ignored(Long eventId, String reason, Long marketOrderId) {
        update(eventId, "IGNORED", "NO_STATE_CHANGE", reason, marketOrderId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(Long eventId, String code, String message) {
        update(eventId, "REJECTED", code, message, null, null);
    }

    @Transactional(readOnly = true)
    public Page<FixMessageEventResponse> list(int page, int size) {
        Long pspId = isolationService.getCurrentUserPspId();
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        if (pspId != null && pspId > 0) {
            return repository.findByPspIdOrderByReceivedAtDesc(pspId, request).map(this::toResponse);
        }
        if (isolationService.isPlatformAdministrator()) return repository.findAll(request).map(this::toResponse);
        throw new SecurityException("A PSP context is required");
    }

    private void update(Long eventId, String outcome, String code, String message,
            Long marketOrderId, Long marketExecutionId) {
        FixMessageEvent event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("FIX message event not found: " + eventId));
        event.setOutcome(outcome);
        event.setErrorCode(code);
        event.setErrorMessage(message == null ? null : message.substring(0, Math.min(message.length(), 4000)));
        if (marketOrderId != null) event.setMarketOrderId(marketOrderId);
        if (marketExecutionId != null) event.setMarketExecutionId(marketExecutionId);
        event.setProcessedAt(LocalDateTime.now());
        repository.save(event);
    }

    private Map<String, Object> sanitized(Message message, Collection<Integer> customEvidenceTags) {
        Set<Integer> tags = new LinkedHashSet<>(STANDARD_EVIDENCE_TAGS);
        if (customEvidenceTags != null) tags.addAll(customEvidenceTags);
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Integer tag : tags) {
            if (tag != null && tag > 0 && message.isSetField(tag)) {
                try {
                    fields.put(String.valueOf(tag), message.getString(tag));
                } catch (FieldNotFound ignored) {
                    // The isSetField check and get can race only with a mutable caller.
                }
            }
        }
        return fields;
    }

    private String first(Message message, int... tags) {
        for (int tag : tags) {
            if (message.isSetField(tag)) {
                try {
                    String value = message.getString(tag);
                    if (value != null && !value.isBlank()) return value;
                } catch (FieldNotFound ignored) {
                    // Continue to the next business reference field.
                }
            }
        }
        return null;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash FIX message evidence", exception);
        }
    }

    private FixMessageEventResponse toResponse(FixMessageEvent event) {
        return new FixMessageEventResponse(event.getId(), event.getSessionId(), event.getDirection(),
                event.getMessageType(), event.getMessageSequenceNumber(), event.getSendingTime(),
                event.getBusinessReference(), event.getMessageHash(), event.getSanitizedFields(),
                event.getOutcome(), event.getErrorCode(), event.getErrorMessage(), event.getMarketOrderId(),
                event.getMarketExecutionId(), event.getReceivedAt(), event.getProcessedAt());
    }
}
