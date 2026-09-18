package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.CustomWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomWatchlistRepository extends JpaRepository<CustomWatchlist, Long> {
    Optional<CustomWatchlist> findByWatchlistName(String watchlistName);
    List<CustomWatchlist> findByStatus(String status);
    List<CustomWatchlist> findByListType(String listType);
}
