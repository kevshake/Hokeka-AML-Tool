package com.posgateway.aml.service.market;

import com.posgateway.aml.entity.integration.FixMessageEvent;
import com.posgateway.aml.repository.integration.FixMessageEventRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.ClOrdID;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixMessageEvidenceServiceTest {
    @Mock
    private FixMessageEventRepository repository;

    @Mock
    private PspIsolationService isolationService;

    @Test
    void identicalReplayReturnsExistingEvidenceEvent() throws Exception {
        SessionID sessionId = new SessionID("FIX.4.4", "HOKEKA", "BROKER");
        Message message = message(15, "ORDER-15");
        FixMessageEvent existing = existingEvent(81L, hash(message.toString()));
        when(repository.findBySessionIdAndMessageSequenceNumberAndDirection(
                sessionId.toString(), 15, "INBOUND")).thenReturn(Optional.of(existing));

        FixMessageEvidenceService service = new FixMessageEvidenceService(repository, isolationService);

        assertEquals(81L, service.receive(9L, sessionId, message, List.of()));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void alteredReplayWithSameSequenceIsRejected() throws Exception {
        SessionID sessionId = new SessionID("FIX.4.4", "HOKEKA", "BROKER");
        Message message = message(15, "ORDER-ALTERED");
        FixMessageEvent existing = existingEvent(81L, hash(message(15, "ORDER-ORIGINAL").toString()));
        when(repository.findBySessionIdAndMessageSequenceNumberAndDirection(
                sessionId.toString(), 15, "INBOUND")).thenReturn(Optional.of(existing));

        FixMessageEvidenceService service = new FixMessageEvidenceService(repository, isolationService);

        assertThrows(SecurityException.class, () -> service.receive(9L, sessionId, message, List.of()));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Message message(int sequence, String clientOrderId) {
        Message message = new Message();
        message.getHeader().setField(new MsgType("D"));
        message.getHeader().setField(new MsgSeqNum(sequence));
        message.setField(new ClOrdID(clientOrderId));
        return message;
    }

    private FixMessageEvent existingEvent(Long id, String messageHash) {
        FixMessageEvent event = new FixMessageEvent();
        event.setId(id);
        event.setMessageHash(messageHash);
        return event;
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
