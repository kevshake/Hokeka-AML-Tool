package com.posgateway.aml.repository.residency;

import com.posgateway.aml.entity.residency.PspEdgeSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Per-PSP CTR and tunnel settings (one row per PSP, keyed by psp_id). */
public interface PspEdgeSettingsRepository extends JpaRepository<PspEdgeSettings, Long> {
}
