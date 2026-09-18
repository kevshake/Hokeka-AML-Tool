package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportSavedFilter;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.reporting.ReportSavedFilterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports/saved-filters")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST', 'INVESTIGATOR')")
public class ReportSavedFilterController {

    private final ReportSavedFilterRepository filterRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportSavedFilterController(ReportSavedFilterRepository filterRepository,
                                       ReportRepository reportRepository,
                                       UserRepository userRepository) {
        this.filterRepository = filterRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ReportSavedFilter> listMine(Authentication authentication,
                                            @RequestParam(required = false) Long reportId) {
        User user = resolveUser(authentication);
        if (reportId != null) {
            return filterRepository.findByUserIdAndReportIdOrderByFilterNameAsc(user.getId(), reportId);
        }
        return filterRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    @PostMapping
    public ResponseEntity<ReportSavedFilter> save(@RequestBody Map<String, Object> body,
                                                  Authentication authentication) {
        User user = resolveUser(authentication);
        Long reportId = resolveReportId(body);
        String filterName = body.get("filterName").toString();
        Long pspId = body.get("pspId") != null ? Long.valueOf(body.get("pspId").toString()) : null;

        ReportSavedFilter filter = filterRepository
                .findByUserIdAndReportIdAndFilterName(user.getId(), reportId, filterName)
                .orElseGet(ReportSavedFilter::new);
        filter.setUserId(user.getId());
        filter.setReportId(reportId);
        filter.setPspId(pspId);
        filter.setFilterName(filterName);
        @SuppressWarnings("unchecked")
        Map<String, Object> criteria = (Map<String, Object>) body.get("filterCriteria");
        filter.setFilterCriteria(criteria);
        if (body.get("isDefault") != null) {
            filter.setIsDefault(Boolean.parseBoolean(body.get("isDefault").toString()));
        }
        return ResponseEntity.status(filter.getId() == null ? HttpStatus.CREATED : HttpStatus.OK)
                .body(filterRepository.save(filter));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = resolveUser(authentication);
        ReportSavedFilter filter = filterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!user.getId().equals(filter.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        filterRepository.delete(filter);
        return ResponseEntity.noContent().build();
    }

    private Long resolveReportId(Map<String, Object> body) {
        if (body.get("reportId") != null) {
            return Long.valueOf(body.get("reportId").toString());
        }
        String reportCode = body.get("reportCode") != null ? body.get("reportCode").toString() : null;
        if (reportCode != null) {
            Report report = reportRepository.findByReportCode(reportCode)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown report code: " + reportCode));
            return report.getId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportId or reportCode required");
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
