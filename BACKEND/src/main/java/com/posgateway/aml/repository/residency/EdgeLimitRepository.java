package com.posgateway.aml.repository.residency;

import com.posgateway.aml.entity.residency.EdgeLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Edge limits distributed in the config document. */
public interface EdgeLimitRepository extends JpaRepository<EdgeLimit, Long> {

    /** Active limits of a PSP in limitId order (stable document ordering). */
    List<EdgeLimit> findByPspIdAndActiveTrueOrderByIdAsc(Long pspId);

    /** One limit of a PSP (tenant-scoped lookup). */
    Optional<EdgeLimit> findByIdAndPspId(Long id, Long pspId);
}
