package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspCustomerComplaintDto;
import com.posgateway.aml.entity.psp.cbk.PspCustomerComplaint;
import com.posgateway.aml.repository.psp.cbk.PspCustomerComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * CBK GDI #5 – Customer Complaints and Remedials (monthly, day 3).
 * Endpoint: /api/v1/psps/{pspId}/cbk/customer-complaints
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/customer-complaints")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspCustomerComplaintController {

    private static final Logger log = LoggerFactory.getLogger(PspCustomerComplaintController.class);

    private final PspCustomerComplaintRepository repository;

    public PspCustomerComplaintController(PspCustomerComplaintRepository repository) {
        this.repository = repository;
    }

    private com.posgateway.aml.entity.User getCurrentUser() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.posgateway.aml.entity.User) {
            return (com.posgateway.aml.entity.User) auth.getPrincipal();
        }
        return null;
    }

    private boolean canAccess(com.posgateway.aml.entity.User user, Long pspId) {
        if (user == null) return false;
        com.posgateway.aml.model.UserRole role =
                com.posgateway.aml.model.UserRole.valueOf(user.getRole().getName());
        if (role == com.posgateway.aml.model.UserRole.PSP_ADMIN) {
            return user.getPsp() != null && user.getPsp().getPspId().equals(pspId);
        }
        return true;
    }

    @GetMapping
    public ResponseEntity<List<PspCustomerComplaint>> list(@PathVariable Long pspId) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspCustomerComplaint> getById(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspCustomerComplaint> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspCustomerComplaint> create(@PathVariable Long pspId,
                                                       @RequestBody PspCustomerComplaintDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspCustomerComplaint e = PspCustomerComplaint.builder()
                .pspId(pspId)
                .complaintId(dto.getComplaintId())
                .complaintCode(dto.getComplaintCode())
                .complainantGender(dto.getComplainantGender())
                .complaintFrequency(dto.getComplaintFrequency())
                .complainantName(dto.getComplainantName())
                .complainantAge(dto.getComplainantAge())
                .complainantContactNumber(dto.getComplainantContactNumber())
                .complainantSubCountyLocation(dto.getComplainantSubCountyLocation())
                .complainantEducationLevel(dto.getComplainantEducationLevel())
                .othersComplainantDetails(dto.getOthersComplainantDetails())
                .agentId(dto.getAgentId())
                .dateOfOccurrence(dto.getDateOfOccurrence())
                .dateReportedToTheInstitution(dto.getDateReportedToTheInstitution())
                .dateResolved(dto.getDateResolved())
                .remedialStatus(dto.getRemedialStatus())
                .amountLost(dto.getAmountLost())
                .amountRecovered(dto.getAmountRecovered())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspCustomerComplaint> update(@PathVariable Long pspId,
                                                       @PathVariable Long id,
                                                       @RequestBody PspCustomerComplaintDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspCustomerComplaint> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspCustomerComplaint e = opt.get();
        e.setComplaintId(dto.getComplaintId());
        e.setComplaintCode(dto.getComplaintCode());
        e.setComplainantGender(dto.getComplainantGender());
        e.setComplaintFrequency(dto.getComplaintFrequency());
        e.setComplainantName(dto.getComplainantName());
        e.setComplainantAge(dto.getComplainantAge());
        e.setComplainantContactNumber(dto.getComplainantContactNumber());
        e.setComplainantSubCountyLocation(dto.getComplainantSubCountyLocation());
        e.setComplainantEducationLevel(dto.getComplainantEducationLevel());
        e.setOthersComplainantDetails(dto.getOthersComplainantDetails());
        e.setAgentId(dto.getAgentId());
        e.setDateOfOccurrence(dto.getDateOfOccurrence());
        e.setDateReportedToTheInstitution(dto.getDateReportedToTheInstitution());
        e.setDateResolved(dto.getDateResolved());
        e.setRemedialStatus(dto.getRemedialStatus());
        e.setAmountLost(dto.getAmountLost());
        e.setAmountRecovered(dto.getAmountRecovered());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspCustomerComplaint> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
