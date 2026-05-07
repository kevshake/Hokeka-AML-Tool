package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspCustomerComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PspCustomerComplaintRepository extends JpaRepository<PspCustomerComplaint, Long> {
    List<PspCustomerComplaint> findByPspId(Long pspId);
    Optional<PspCustomerComplaint> findByComplaintId(String complaintId);
    List<PspCustomerComplaint> findByPspIdAndRemedialStatus(Long pspId, String remedialStatus);
}
