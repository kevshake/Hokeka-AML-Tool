package com.posgateway.aml.repository;

import com.posgateway.aml.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /**
     * Find a user's settings by their user ID.
     * Returns empty if no settings row exists yet (defaults should be used).
     */
    Optional<UserSettings> findByUserId(Long userId);
}
