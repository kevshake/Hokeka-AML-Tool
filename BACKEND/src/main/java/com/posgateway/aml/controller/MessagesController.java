package com.posgateway.aml.controller;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.messaging.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Messages Controller.
 *
 * <p>Internal messaging / notifications scoped to the authenticated user.
 * All endpoints read or write rows owned by the {@code @AuthenticationPrincipal}
 * — there is no admin override here.
 */
@RestController
@RequestMapping("/messages")
@PreAuthorize("isAuthenticated()")
public class MessagesController {

    private final MessageService messageService;

    public MessagesController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMessages(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(messageService.listForUser(currentUser.getId(), unreadOnly));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal User currentUser,
                                           @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        boolean ok = messageService.markRead(id, currentUser.getId());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        int updated = messageService.markAllRead(currentUser.getId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        long count = messageService.getUnreadCount(currentUser.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }
}
