package com.posgateway.aml.service.messaging;

import com.posgateway.aml.entity.messaging.Message;
import com.posgateway.aml.repository.messaging.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForUser(Long recipientUserId, boolean unreadOnly) {
        Pageable page = PageRequest.of(0, DEFAULT_PAGE_SIZE);
        Page<Message> rows = unreadOnly
                ? repository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(recipientUserId, page)
                : repository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, page);
        return rows.getContent().stream().map(MessageService::toView).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientUserId) {
        return repository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    @Transactional
    public boolean markRead(Long messageId, Long recipientUserId) {
        Optional<Message> opt = repository.findByIdAndRecipientUserId(messageId, recipientUserId);
        if (opt.isEmpty()) {
            return false;
        }
        Message m = opt.get();
        if (m.getReadAt() == null) {
            m.setReadAt(LocalDateTime.now());
            repository.save(m);
        }
        return true;
    }

    @Transactional
    public int markAllRead(Long recipientUserId) {
        return repository.markAllReadForRecipient(recipientUserId, LocalDateTime.now());
    }

    @Transactional
    public Message send(Message message) {
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        return repository.save(message);
    }

    private static Map<String, Object> toView(Message m) {
        // LinkedHashMap to preserve a stable JSON ordering for the FE.
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("subject", m.getSubject());
        v.put("body", m.getBody());
        v.put("category", m.getCategory() != null ? m.getCategory().name() : null);
        v.put("senderUserId", m.getSenderUserId());
        v.put("recipientUserId", m.getRecipientUserId());
        v.put("relatedEntityType", m.getRelatedEntityType());
        v.put("relatedEntityId", m.getRelatedEntityId());
        v.put("createdAt", m.getCreatedAt());
        v.put("readAt", m.getReadAt());
        v.put("read", m.getReadAt() != null);
        return v;
    }
}
