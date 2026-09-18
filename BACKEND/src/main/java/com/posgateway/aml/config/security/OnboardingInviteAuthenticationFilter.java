package com.posgateway.aml.config.security;

import com.posgateway.aml.service.auth.OnboardingInviteService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Converts a valid invite header into a narrowly scoped onboarding authority. */
@Component
public class OnboardingInviteAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Onboarding-Invite";
    public static final String INVITE_PSP_ID = OnboardingInviteAuthenticationFilter.class.getName() + ".pspId";

    private final OnboardingInviteService inviteService;

    public OnboardingInviteAuthenticationFilter(OnboardingInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().endsWith("/merchants/onboard");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            inviteService.validate(request.getHeader(HEADER)).ifPresent(invite -> {
                var auth = new UsernamePasswordAuthenticationToken("onboarding-invite", null,
                        List.of(new SimpleGrantedAuthority("ONBOARDING_INVITE")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                request.setAttribute(INVITE_PSP_ID,
                        invite.getPsp() == null ? null : invite.getPsp().getPspId());
            });
        }
        chain.doFilter(request, response);
    }
}
