package com.posgateway.aml.repository.auth;

import com.posgateway.aml.entity.auth.OnboardingInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OnboardingInviteRepository extends JpaRepository<OnboardingInvite, Long> {
    Optional<OnboardingInvite> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash, LocalDateTime now);
}
