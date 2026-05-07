package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PspCyberIncidentRepository extends JpaRepository<PspCyberIncident, Long> {
    List<PspCyberIncident> findByPspId(Long pspId);
    Optional<PspCyberIncident> findByIncidentNumber(String incidentNumber);
}
