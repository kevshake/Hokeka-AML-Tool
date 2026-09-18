package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.SanctionsList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SanctionsListRepository extends JpaRepository<SanctionsList, Integer> {

    List<SanctionsList> findAllByOrderByDownloadedAtDesc();

    List<SanctionsList> findByListNameOrderByDownloadedAtDesc(String listName);

    Optional<SanctionsList> findByListNameAndVersion(String listName, String version);
}
