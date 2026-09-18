package com.posgateway.aml.controller.psp;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.posgateway.aml.entity.User;
import com.posgateway.aml.dto.psp.PspLoginRequest;
import com.posgateway.aml.dto.psp.PspRegistrationRequest;
import com.posgateway.aml.dto.psp.PspResponse;
import com.posgateway.aml.dto.psp.PspStatusUpdateRequest;
import com.posgateway.aml.dto.psp.PspUserCreationRequest;
import com.posgateway.aml.dto.psp.PspUpdateRequest;
import com.posgateway.aml.dto.psp.PspUserResponse;
import com.posgateway.aml.dto.psp.PspCbkConfigRequest;
import com.posgateway.aml.dto.psp.PspCbkConfigResponse;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkProperties;
import com.posgateway.aml.mapper.PspMapper;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.psp.PspService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// @Slf4j removed
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/psps")
public class PspController {

    private static final Logger log = LoggerFactory.getLogger(PspController.class);

    private final PspService pspService;
    private final PspMapper pspMapper;
    private final PspRepository pspRepository;
    private final CbkProperties cbkProperties;

    public PspController(PspService pspService,
                         PspMapper pspMapper,
                         PspRepository pspRepository,
                         CbkProperties cbkProperties) {
        this.pspService = pspService;
        this.pspMapper = pspMapper;
        this.pspRepository = pspRepository;
        this.cbkProperties = cbkProperties;
    }


    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR')")
    public ResponseEntity<List<PspResponse>> getAllPsps() {
        List<PspResponse> list = pspRepository.findAll().stream()
                .map(pspMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','INVESTIGATOR')")
    public ResponseEntity<PspResponse> getPsp(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        if (!canAccessPsp(currentUser, id)) return ResponseEntity.status(403).build();
        return pspRepository.findById(id)
                .map(pspMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PspResponse> getMyPsp(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null || currentUser.getPsp() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(pspMapper.toResponse(currentUser.getPsp()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> deletePsp(@PathVariable Long id) {
        if (!pspRepository.existsById(id)) return ResponseEntity.notFound().build();
        pspRepository.deleteById(id);
        log.info("Deleted PSP {}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<PspResponse> registerPsp(@RequestBody PspRegistrationRequest request) {
        log.info("Received PSP registration request");
        Psp psp = pspService.registerPsp(request);
        return ResponseEntity.ok(pspMapper.toResponse(psp));
    }

    @PostMapping("/register")
    public ResponseEntity<PspResponse> publicRegisterPsp(@RequestBody PspRegistrationRequest request) {
        log.info("Received public PSP registration request");
        Psp psp = pspService.registerPsp(request);
        return ResponseEntity.ok(pspMapper.toResponse(psp));
    }

    // NOTE: the previous `listPsps()` @GetMapping("") method was a duplicate of
    // getAllPsps() above and produced an ambiguous-mapping error on startup.
    // Removed to unblock the local h2 profile boot.

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> updatePspStatus(@PathVariable Long id, @RequestBody PspStatusUpdateRequest request) {
        log.info("Received status update for PSP {}", id);
        pspService.updatePspStatus(id, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN')")
    public ResponseEntity<PspResponse> updatePspProfile(@PathVariable Long id, @RequestBody PspUpdateRequest request,
                                                        @AuthenticationPrincipal User currentUser) {
        if (!canAccessPsp(currentUser, id)) return ResponseEntity.status(403).build();
        log.info("Received profile update for PSP {}", id);
        Psp psp = pspService.updatePspProfile(id, request);
        return ResponseEntity.ok(pspMapper.toResponse(psp));
    }

    /**
     * Toggle KYC/KYB enforcement for a PSP. RESTRICTED to platform admins — a PSP must
     * not be able to disable its own KYC. When disabled, that PSP's merchants onboard as
     * ACTIVE with kycStatus=NOT_REQUIRED and transactions flow without KYC gating. The
     * change is captured by the audit-logging aspect that wraps mutating controllers.
     */
    @PutMapping("/{id}/kyc-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<PspResponse> updateKycEnabled(@PathVariable Long id,
                                                        @RequestParam("enabled") boolean enabled) {
        Optional<Psp> opt = pspRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Psp psp = opt.get();
        psp.setKycEnabled(enabled);
        Psp saved = pspRepository.save(psp);
        log.warn("KYC {} for PSP {} (code={}) by platform admin",
                enabled ? "ENABLED" : "DISABLED", id, saved.getPspCode());
        return ResponseEntity.ok(pspMapper.toResponse(saved));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN')")
    public ResponseEntity<PspUserResponse> createPspUser(@RequestBody PspUserCreationRequest request,
                                                         @AuthenticationPrincipal User currentUser) {
        if (!canAccessPsp(currentUser, request.getPspId())) return ResponseEntity.status(403).build();
        log.info("Received PSP user creation request");
        User user = pspService.createPspUser(request);
        return ResponseEntity.ok(pspMapper.toResponse(user));
    }

    @PostMapping("/auth/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PspUserResponse> login(@RequestBody PspLoginRequest request) {
        Optional<User> userOpt = pspService.authenticatePspUser(request.getEmail(), request.getPassword());
        return userOpt.map(user -> ResponseEntity.ok(pspMapper.toResponse(user)))
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Read CBK regulatory configuration for a PSP. Available to platform admins
     * AND the PSP_ADMIN of that PSP (so the PSP can SEE the chosen environment
     * but not change it).
     */
    @GetMapping("/{id}/cbk-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<PspCbkConfigResponse> getCbkConfig(@PathVariable Long id,
                                                             @AuthenticationPrincipal User currentUser) {
        if (!canAccessPsp(currentUser, id)) return ResponseEntity.status(403).build();
        return pspRepository.findById(id)
                .map(this::toCbkConfigResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update CBK regulatory configuration for a PSP.
     *
     * <p>RESTRICTED to platform admins. The CBK environment (live vs preprod) is a
     * platform-level decision because it changes which regulator endpoint receives
     * production data — PSP_ADMINs cannot self-promote. Spring Security
     * blocks PSP_ADMIN at the {@code @PreAuthorize} layer.
     */
    @PutMapping("/{id}/cbk-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN')")
    public ResponseEntity<PspCbkConfigResponse> updateCbkConfig(
            @PathVariable Long id,
            @RequestBody PspCbkConfigRequest body,
            @AuthenticationPrincipal User currentUser) {

        if (!canAccessPsp(currentUser, id)) return ResponseEntity.status(403).build();

        Optional<Psp> opt = pspRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Psp psp = opt.get();

        if (body.getCbkInstitutionCode() != null) psp.setCbkInstitutionCode(body.getCbkInstitutionCode());
        if (body.getCbkReportingEnabled() != null) psp.setCbkReportingEnabled(body.getCbkReportingEnabled());
        if (body.getCbkClientId() != null) psp.setCbkClientId(body.getCbkClientId());
        // Treat blank-string secret as "leave unchanged" so the FE can omit secrets safely.
        if (body.getCbkClientSecret() != null && !body.getCbkClientSecret().isBlank()) {
            psp.setCbkClientSecret(body.getCbkClientSecret());
        }
        boolean platformAdmin = currentUser != null && currentUser.getPsp() == null;
        if (platformAdmin && body.getCbkEnvironment() != null) {
            String env = body.getCbkEnvironment().toLowerCase();
            if (!"live".equals(env) && !"preprod".equals(env)) {
                return ResponseEntity.badRequest().build();
            }
            psp.setCbkEnvironment(env);
        }
        if (platformAdmin && body.getCbkAllowLive() != null) psp.setCbkAllowLive(body.getCbkAllowLive());

        Psp saved = pspRepository.save(psp);
        log.info("CBK config updated for PSP {}: env={} allowLive={} reportingEnabled={}",
                id, saved.getCbkEnvironment(), saved.getCbkAllowLive(), saved.getCbkReportingEnabled());
        return ResponseEntity.ok(toCbkConfigResponse(saved));
    }

    private PspCbkConfigResponse toCbkConfigResponse(Psp psp) {
        PspCbkConfigResponse r = new PspCbkConfigResponse();
        r.setPspId(psp.getPspId());
        r.setPspCode(psp.getPspCode());
        r.setLegalName(psp.getLegalName());
        r.setCbkInstitutionCode(psp.getCbkInstitutionCode());
        r.setCbkReportingEnabled(psp.getCbkReportingEnabled());
        r.setCbkEnvironment(psp.getCbkEnvironment());
        r.setCbkAllowLive(psp.getCbkAllowLive());
        r.setCbkClientId(psp.getCbkClientId());
        r.setHasClientSecret(psp.getCbkClientSecret() != null && !psp.getCbkClientSecret().isBlank());
        r.setLiveEffective(
                cbkProperties.isAllowLive()
                        && Boolean.TRUE.equals(psp.getCbkAllowLive())
                        && "live".equalsIgnoreCase(psp.getCbkEnvironment()));
        return r;
    }

    private boolean canAccessPsp(User currentUser, Long pspId) {
        if (currentUser == null || pspId == null) return false;
        if (currentUser.getPsp() == null) return true;
        return pspId.equals(currentUser.getPsp().getPspId());
    }
}
