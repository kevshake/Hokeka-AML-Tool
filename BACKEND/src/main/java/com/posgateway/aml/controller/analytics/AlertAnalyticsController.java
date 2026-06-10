package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.repository.AlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Alert trend analytics — GET /analytics/alert-trends?days=N
 * Returns daily counts of open, resolved (closed/false_positive), and escalated alerts.
 */
@RestController
@RequestMapping("/analytics")
@PreAuthorize("isAuthenticated()")
public class AlertAnalyticsController {

    private final AlertRepository alertRepository;

    public AlertAnalyticsController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping("/alert-trends")
    public ResponseEntity<List<Map<String, Object>>> getAlertTrends(
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Alert> alerts = alertRepository.findByCreatedAtAfter(since);

        // Group by date
        Map<LocalDate, List<Alert>> byDate = alerts.stream()
                .collect(Collectors.groupingBy(a -> a.getCreatedAt().toLocalDate()));

        // Build sorted list covering every day in the window
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1);
        LocalDate end = LocalDate.now();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<Alert> dayAlerts = byDate.getOrDefault(d, Collections.emptyList());
            long open = dayAlerts.stream().filter(a -> "open".equalsIgnoreCase(a.getStatus())).count();
            long resolved = dayAlerts.stream()
                    .filter(a -> "closed".equalsIgnoreCase(a.getStatus())
                            || "false_positive".equalsIgnoreCase(a.getStatus())).count();
            long escalated = dayAlerts.stream()
                    .filter(a -> "escalated".equalsIgnoreCase(a.getStatus())).count();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", d.toString());
            entry.put("open", open);
            entry.put("resolved", resolved);
            entry.put("escalated", escalated);
            entry.put("total", dayAlerts.size());
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }
}
