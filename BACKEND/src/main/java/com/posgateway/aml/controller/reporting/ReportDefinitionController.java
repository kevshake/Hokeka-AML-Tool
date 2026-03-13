package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.dto.reporting.ReportCategoryDTO;
import com.posgateway.aml.dto.reporting.ReportDefinitionDTO;
import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportCategory;
import com.posgateway.aml.entity.reporting.ReportDefinition;
import com.posgateway.aml.entity.reporting.ReportType;
import com.posgateway.aml.repository.reporting.ReportDefinitionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Report Definition Controller
 * REST endpoints for report definitions and categories
 */
@RestController
@RequestMapping("/api/reports/definitions")
public class ReportDefinitionController {

    private static final Logger logger = LoggerFactory.getLogger(ReportDefinitionController.class);

    private final ReportRepository reportRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final PspIsolationService pspIsolationService;

    public ReportDefinitionController(ReportRepository reportRepository,
                                      ReportDefinitionRepository reportDefinitionRepository,
                                      PspIsolationService pspIsolationService) {
        this.reportRepository = reportRepository;
        this.reportDefinitionRepository = reportDefinitionRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * List all report definitions
     * GET /api/reports/definitions
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ANALYST')")
    public ResponseEntity<Page<ReportDefinitionDTO>> listReportDefinitions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "reportName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        logger.debug("List report definitions - category: {}, type: {}, search: {}", category, type, search);
        
        Sort sort = Sort.by(sortDirection.equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        // Parse category filter
        ReportCategory categoryFilter = null;
        if (category != null && !category.isEmpty()) {
            try {
                categoryFilter = ReportCategory.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid category filter: {}", category);
            }
        }
        
        // Parse type filter
        ReportType typeFilter = null;
        if (type != null && !type.isEmpty()) {
            try {
                typeFilter = ReportType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid type filter: {}", type);
            }
        }
        
        Page<Report> reports = reportRepository.findByFilters(categoryFilter, typeFilter, search, pageable);
        
        Page<ReportDefinitionDTO> dtoPage = reports.map(this::convertToDTO);
        
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Get specific report definition by ID
     * GET /api/reports/definitions/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ANALYST')")
    public ResponseEntity<ReportDefinitionDTO> getReportDefinition(@PathVariable Long id) {
        logger.debug("Get report definition by ID: {}", id);
        
        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));
        
        return ResponseEntity.ok(convertToDTO(report));
    }

    /**
     * Get report definition by code
     * GET /api/reports/definitions/code/{code}
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ANALYST')")
    public ResponseEntity<ReportDefinitionDTO> getReportDefinitionByCode(@PathVariable String code) {
        logger.debug("Get report definition by code: {}", code);
        
        Report report = reportRepository.findByReportCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + code));
        
        return ResponseEntity.ok(convertToDTO(report));
    }

    /**
     * List all report categories
     * GET /api/reports/categories
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ANALYST')")
    public ResponseEntity<List<ReportCategoryDTO>> listCategories() {
        logger.debug("List report categories");
        
        List<ReportCategoryDTO> categories = Arrays.stream(ReportCategory.values())
            .map(this::convertToCategoryDTO)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(categories);
    }

    /**
     * Get reports by category
     * GET /api/reports/definitions/category/{category}
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ANALYST')")
    public ResponseEntity<ReportCategoryDTO> getReportsByCategory(@PathVariable String category) {
        logger.debug("Get reports by category: {}", category);
        
        ReportCategory reportCategory;
        try {
            reportCategory = ReportCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
        
        List<Report> reports = reportRepository.findByReportCategoryAndEnabledTrue(reportCategory);
        
        ReportCategoryDTO dto = new ReportCategoryDTO();
        dto.setCategory(reportCategory);
        dto.setDisplayName(reportCategory.getDisplayName());
        dto.setDescription(reportCategory.getDescription());
        dto.setReportCount(reports.size());
        dto.setReports(reports.stream().map(this::convertToDTO).collect(Collectors.toList()));
        
        return ResponseEntity.ok(dto);
    }

    /**
     * Get report types
     * GET /api/reports/definitions/types
     */
    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ANALYST')")
    public ResponseEntity<List<Map<String, String>>> getReportTypes() {
        logger.debug("Get report types");
        
        List<Map<String, String>> types = Arrays.stream(ReportType.values())
            .map(t -> Map.of(
                "code", t.name(),
                "displayName", t.getDisplayName(),
                "description", t.getDescription()
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(types);
    }

    /**
     * Get category statistics
     * GET /api/reports/definitions/statistics/categories
     */
    @GetMapping("/statistics/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'PSP_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCategoryStatistics() {
        logger.debug("Get category statistics");
        
        List<Object[]> counts = reportRepository.countByCategory();
        
        List<Map<String, Object>> statistics = new ArrayList<>();
        for (Object[] count : counts) {
            ReportCategory category = (ReportCategory) count[0];
            Long cnt = (Long) count[1];
            
            Map<String, Object> stat = Map.of(
                "category", category.name(),
                "displayName", category.getDisplayName(),
                "count", cnt
            );
            statistics.add(stat);
        }
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get report definition versions
     * GET /api/reports/definitions/{id}/versions
     */
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getReportVersions(@PathVariable Long id) {
        logger.debug("Get report versions for report: {}", id);
        
        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));
        
        List<ReportDefinition> definitions = reportDefinitionRepository.findByReportId(id);
        
        List<Map<String, Object>> versions = definitions.stream()
            .map(d -> {
                Map<String, Object> version = new java.util.HashMap<>();
                version.put("id", d.getId());
                version.put("version", d.getVersion());
                version.put("isActive", d.getIsActive());
                version.put("createdAt", d.getCreatedAt());
                version.put("createdBy", d.getCreatedBy() != null ? d.getCreatedBy().getFullName() : null);
                return version;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(versions);
    }

    /**
     * Convert Report entity to DTO
     */
    private ReportDefinitionDTO convertToDTO(Report report) {
        ReportDefinitionDTO dto = new ReportDefinitionDTO();
        dto.setId(report.getId());
        dto.setReportCode(report.getReportCode());
        dto.setReportName(report.getReportName());
        dto.setReportCategory(report.getReportCategory());
        dto.setCategoryDisplayName(report.getReportCategory() != null ? report.getReportCategory().getDisplayName() : null);
        dto.setDescription(report.getDescription());
        dto.setReportType(report.getReportType());
        dto.setBaseEntity(report.getBaseEntity());
        dto.setRequiresApproval(report.getRequiresApproval());
        dto.setRegulatoryTemplate(report.getRegulatoryTemplate());
        dto.setRetentionDays(report.getRetentionDays());
        dto.setEnabled(report.getEnabled());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());
        
        // Get active definition details
        ReportDefinition activeDef = report.getActiveDefinition();
        if (activeDef != null) {
            dto.setCurrentVersion(activeDef.getVersion());
            dto.setParameters(activeDef.getParameters());
            dto.setFilters(activeDef.getFilters());
            dto.setColumns(activeDef.getColumns());
        }
        
        return dto;
    }

    /**
     * Convert ReportCategory to DTO
     */
    private ReportCategoryDTO convertToCategoryDTO(ReportCategory category) {
        ReportCategoryDTO dto = new ReportCategoryDTO();
        dto.setCategory(category);
        dto.setDisplayName(category.getDisplayName());
        dto.setDescription(category.getDescription());
        
        // Count reports in category
        List<Report> reports = reportRepository.findByReportCategoryAndEnabledTrue(category);
        dto.setReportCount(reports.size());
        
        return dto;
    }
}
