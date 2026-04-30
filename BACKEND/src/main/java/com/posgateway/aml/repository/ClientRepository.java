package com.posgateway.aml.repository;

import com.posgateway.aml.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Client Entity
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Find client by API key
     */
    Optional<Client> findByApiKey(String apiKey);

    /**
     * Find clients by status
     */
    List<Client> findByStatus(String status);

    /**
     * Find active clients
     */
    List<Client> findByStatusOrderByClientNameAsc(String status);

    /**
     * Check if API key exists
     */
    boolean existsByApiKey(String apiKey);

    /**
     * Update last accessed timestamp
     */
    @Modifying
    @Query("UPDATE Client c SET c.lastAccessedAt = :timestamp WHERE c.apiKey = :apiKey")
    void updateLastAccessedAt(@Param("apiKey") String apiKey, @Param("timestamp") LocalDateTime timestamp);
}

