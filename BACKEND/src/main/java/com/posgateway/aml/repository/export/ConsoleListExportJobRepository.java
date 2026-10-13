package com.posgateway.aml.repository.export;

import com.posgateway.aml.entity.export.ConsoleListExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** JPA access for async Console list export job rows. */
public interface ConsoleListExportJobRepository extends JpaRepository<ConsoleListExportJob, Long> {

    /** Loads a job by its public UUID for status polling and download authorization checks. */
    Optional<ConsoleListExportJob> findByJobUuid(String jobUuid);
}
