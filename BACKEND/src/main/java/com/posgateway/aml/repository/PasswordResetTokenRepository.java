package com.posgateway.aml.repository;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.auth.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    List<PasswordResetToken> findByUserAndUsedAtIsNull(User user);

    List<PasswordResetToken> findByExpiresAtBefore(LocalDateTime cutoff);
}


