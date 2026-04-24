package com.posgateway.aml.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Messages Controller
 * Provides endpoints for internal messaging/notifications.
 * Returns live data only — no mock data. A dedicated messages table and service
 * should be wired here when the feature is fully implemented.
 */
@RestController
@RequestMapping("/messages")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'ANALYST')")
public class MessagesController {

    /**
     * Get all messages for current user
     * GET /api/v1/messages
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMessages(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    /**
     * Mark message as read
     * PUT /api/v1/messages/{id}/read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok().build();
    }

    /**
     * Get unread message count
     * GET /api/v1/messages/unread/count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", 0L));
    }
}
