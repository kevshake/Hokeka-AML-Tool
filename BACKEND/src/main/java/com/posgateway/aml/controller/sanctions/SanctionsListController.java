package com.posgateway.aml.controller.sanctions;

import com.posgateway.aml.entity.sanctions.SanctionsList;
import com.posgateway.aml.repository.sanctions.SanctionsListRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read API for sanctions list download metadata ({@code sanctions_lists} table).
 */
@RestController
@RequestMapping("/sanctions/lists")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'ANALYST', 'PSP_ADMIN')")
public class SanctionsListController {

    private final SanctionsListRepository sanctionsListRepository;

    public SanctionsListController(SanctionsListRepository sanctionsListRepository) {
        this.sanctionsListRepository = sanctionsListRepository;
    }

    @GetMapping
    public ResponseEntity<List<SanctionsList>> listAll(
            @RequestParam(required = false) String listName) {
        if (listName != null && !listName.isBlank()) {
            return ResponseEntity.ok(sanctionsListRepository.findByListNameOrderByDownloadedAtDesc(listName));
        }
        return ResponseEntity.ok(sanctionsListRepository.findAllByOrderByDownloadedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SanctionsList> getById(@PathVariable Integer id) {
        return sanctionsListRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
