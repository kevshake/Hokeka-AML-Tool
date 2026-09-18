package com.posgateway.aml.controller.admin;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.auth.OnboardingInviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/onboarding/invites")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PLATFORM_ADMIN')")
public class OnboardingInviteAdminController {
    private final OnboardingInviteService service;

    public OnboardingInviteAdminController(OnboardingInviteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OnboardingInviteService.IssuedInvite> issue(
            @RequestBody InviteRequest request, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.issue(request.pspId(), request.role(), request.expiresAt(),
                user == null ? null : user.getId()));
    }

    public record InviteRequest(Long pspId, String role, LocalDateTime expiresAt) {}
}
