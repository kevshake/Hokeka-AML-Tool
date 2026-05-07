package com.posgateway.aml.repository.messaging;

import com.posgateway.aml.entity.messaging.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    Page<Message> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    Optional<Message> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :now " +
           "WHERE m.recipientUserId = :recipientUserId AND m.readAt IS NULL")
    int markAllReadForRecipient(@Param("recipientUserId") Long recipientUserId,
                                @Param("now") LocalDateTime now);
}
