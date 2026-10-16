package com.posgateway.aml.repository.residency;

import com.posgateway.aml.entity.residency.PspDataSettingChange;
import com.posgateway.aml.entity.residency.PspDataSettingChange.Setting;
import com.posgateway.aml.entity.residency.PspDataSettingChange.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Maker-checker change requests for PSP data residency settings. */
public interface PspDataSettingChangeRepository extends JpaRepository<PspDataSettingChange, Long> {

    /** Every request of a PSP, newest first (Console history). */
    List<PspDataSettingChange> findByPspIdOrderByRequestedAtDesc(Long pspId);

    /** Requests of a PSP in the given states, oldest first. */
    List<PspDataSettingChange> findByPspIdAndStatusInOrderByRequestedAtAsc(Long pspId, Collection<Status> statuses);

    /** The request of one setting awaiting a checker (at most one: partial unique index in V261). */
    Optional<PspDataSettingChange> findFirstByPspIdAndSettingAndStatus(Long pspId, Setting setting, Status status);

    /** Approved requests of one setting not yet in force (a waiting retention reduction). */
    List<PspDataSettingChange> findByPspIdAndSettingAndStatus(Long pspId, Setting setting, Status status);

    /** Approved requests across all PSPs whose effective time has come (scheduled apply job). */
    List<PspDataSettingChange> findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(Status status, Instant now);

    /** Approved requests of one PSP whose effective time has come. */
    List<PspDataSettingChange> findByPspIdAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
            Long pspId, Status status, Instant now);
}
