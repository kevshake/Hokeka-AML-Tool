package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.WatchlistUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistUpdateRepository extends JpaRepository<WatchlistUpdate, Long> {
    Optional<WatchlistUpdate> findByListNameAndListTypeAndUpdateDate(String listName, String listType, java.time.LocalDate updateDate);
    List<WatchlistUpdate> findByListNameAndListType(String listName, String listType);
    List<WatchlistUpdate> findByStatus(String status);
}

