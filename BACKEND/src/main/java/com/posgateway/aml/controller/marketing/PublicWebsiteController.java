package com.posgateway.aml.controller.marketing;

import com.posgateway.aml.dto.marketing.WebsiteContactRequest;
import com.posgateway.aml.dto.marketing.WebsiteContactResponse;
import com.posgateway.aml.service.marketing.WebsiteContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated marketing-site surfaces (demo / contact form).
 */
@RestController
@RequestMapping("/public/website")
public class PublicWebsiteController {

    private final WebsiteContactService websiteContactService;

    public PublicWebsiteController(WebsiteContactService websiteContactService) {
        this.websiteContactService = websiteContactService;
    }

    /**
     * Accept a validated contact or demo request from the public website and persist it.
     */
    @PostMapping("/contact")
    public ResponseEntity<WebsiteContactResponse> submitContact(
            @Valid @RequestBody WebsiteContactRequest body,
            HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        WebsiteContactResponse response = websiteContactService.submit(body, clientIp, userAgent);
        return ResponseEntity.accepted().body(response);
    }

    /** Honour X-Forwarded-For when behind nginx, else use the direct remote address. */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
