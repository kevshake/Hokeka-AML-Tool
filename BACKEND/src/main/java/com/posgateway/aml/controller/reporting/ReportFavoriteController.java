package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportFavorite;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.reporting.ReportFavoriteRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reports/favorites")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'ANALYST', 'PSP_ADMIN', 'PSP_USER')")
public class ReportFavoriteController {

    private final ReportFavoriteRepository favoriteRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportFavoriteController(ReportFavoriteRepository favoriteRepository,
                                    ReportRepository reportRepository,
                                    UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        User user = resolveUser(authentication);
        return favoriteRepository.findByUserIdOrderByDisplayOrderAscCreatedAtDesc(user.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> body,
                                                   Authentication authentication) {
        User user = resolveUser(authentication);
        Long pspId = body.get("pspId") != null ? Long.valueOf(body.get("pspId").toString()) : null;
        Integer order = body.get("displayOrder") != null
                ? Integer.valueOf(body.get("displayOrder").toString()) : 0;

        ResolvedReport resolved = resolveReport(body);
        if (resolved.reportId() != null
                && favoriteRepository.findByUserIdAndReportIdAndPspId(user.getId(), resolved.reportId(), pspId).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (resolved.reportCode() != null
                && favoriteRepository.findByUserIdAndReportCodeAndPspId(user.getId(), resolved.reportCode(), pspId).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ReportFavorite fav = new ReportFavorite();
        fav.setUserId(user.getId());
        fav.setReportId(resolved.reportId());
        fav.setReportCode(resolved.reportCode());
        fav.setPspId(pspId);
        fav.setDisplayOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(favoriteRepository.save(fav)));
    }

    @DeleteMapping("/{reportKey}")
    public ResponseEntity<Void> remove(@PathVariable String reportKey,
                                       @RequestParam(required = false) Long pspId,
                                       Authentication authentication) {
        User user = resolveUser(authentication);
        try {
            Long reportId = Long.parseLong(reportKey);
            favoriteRepository.deleteByUserIdAndReportIdAndPspId(user.getId(), reportId, pspId);
        } catch (NumberFormatException e) {
            favoriteRepository.deleteByUserIdAndReportCodeAndPspId(user.getId(), reportKey, pspId);
        }
        return ResponseEntity.noContent().build();
    }

    private ResolvedReport resolveReport(Map<String, Object> body) {
        if (body.get("reportId") != null) {
            Long reportId = Long.valueOf(body.get("reportId").toString());
            String code = reportRepository.findById(reportId).map(Report::getReportCode).orElse(null);
            return new ResolvedReport(reportId, code);
        }
        String reportCode = body.get("reportCode") != null
                ? body.get("reportCode").toString()
                : body.get("reportId") != null ? null : null;
        if (reportCode == null && body.get("id") != null) {
            reportCode = body.get("id").toString();
        }
        if (reportCode != null) {
            final String code = reportCode;
            return reportRepository.findByReportCode(code)
                    .or(() -> reportRepository.findAll().stream()
                            .filter(r -> slugMatches(r, code))
                            .findFirst())
                    .map(r -> new ResolvedReport(r.getId(), code))
                    .orElse(new ResolvedReport(null, code));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportId or reportCode required");
    }

    private Long resolveReportIdFromKey(String reportKey) {
        try {
            return Long.parseLong(reportKey);
        } catch (NumberFormatException e) {
            return reportRepository.findByReportCode(reportKey)
                    .or(() -> reportRepository.findAll().stream()
                            .filter(r -> slugMatches(r, reportKey))
                            .findFirst())
                    .map(Report::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
    }

    private boolean slugMatches(Report report, String slug) {
        if (report.getReportCode() != null && report.getReportCode().equalsIgnoreCase(slug)) {
            return true;
        }
        if (report.getReportName() == null) {
            return false;
        }
        String normalized = report.getReportName().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return normalized.equals(slug.toLowerCase());
    }

    private Map<String, Object> toDto(ReportFavorite fav) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", fav.getId());
        dto.put("reportId", fav.getReportId());
        dto.put("reportCode", fav.getReportCode());
        dto.put("pspId", fav.getPspId());
        dto.put("displayOrder", fav.getDisplayOrder());
        dto.put("createdAt", fav.getCreatedAt());
        return dto;
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private record ResolvedReport(Long reportId, String reportCode) {}
}
