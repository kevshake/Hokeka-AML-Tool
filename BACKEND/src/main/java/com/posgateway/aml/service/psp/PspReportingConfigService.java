package com.posgateway.aml.service.psp;



import com.posgateway.aml.dto.psp.PspReportConfigRequest;
import com.posgateway.aml.dto.psp.PspReportConfigResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.psp.PspReportConfig;
import com.posgateway.aml.repository.PspReportConfigRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @RequiredArgsConstructor removed
@Service
public class PspReportingConfigService {

    private final PspReportConfigRepository configRepository;
    private final PspRepository pspRepository;
    private final UserRepository userRepository;

    public PspReportingConfigService(PspReportConfigRepository configRepository, PspRepository pspRepository, UserRepository userRepository) {
        this.configRepository = configRepository;
        this.pspRepository = pspRepository;
        this.userRepository = userRepository;
    }


    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private void checkAccess(Long pspId) {
        User user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }

        boolean isGlobalAdmin = user.getRole().getName().equals("ADMIN") ||
                user.getRole().getName().equals("APP_CONTROLLER");

        if (isGlobalAdmin) {
            return; // Allowed
        }

        // PSP Admin/User check
        if (user.getPsp() == null || !user.getPsp().getPspId().equals(pspId)) {
            throw new AccessDeniedException("You are not authorized to manage configuration for this PSP");
        }
    }

    @Transactional(readOnly = true)
    public PspReportConfigResponse getConfig(Long pspId) {
        checkAccess(pspId);

        PspReportConfig config = configRepository.findByPsp_PspId(pspId)
                .orElse(null);

        if (config == null) {
            // Return empty or default response if not configured yet, or throw 404. Service
            // choice.
            // Usually better to return null or throw 404. Let's return a default "not
            // configured" object or throw.
            // Throwing allows controller to handle 404.
            throw new IllegalArgumentException("Configuration not found for PSP ID: " + pspId);
        }

        return toResponse(config);
    }

    @Transactional
    public PspReportConfigResponse updateConfig(Long pspId, PspReportConfigRequest request) {
        checkAccess(pspId);

        PspReportConfig config = configRepository.findByPsp_PspId(pspId)
                .orElse(null);

        if (config == null) {
            // Create new
            Psp psp = pspRepository.findById(pspId)
                    .orElseThrow(() -> new IllegalArgumentException("PSP not found: " + pspId));

            config = PspReportConfig.builder()
                    .psp(psp)
                    .build();
        }

        if (request.getReportUrl() != null)
            config.setReportUrl(request.getReportUrl());
        if (request.getAllowedDomains() != null)
            config.setAllowedDomains(request.getAllowedDomains());
        if (request.getAllowedIps() != null)
            config.setAllowedIps(request.getAllowedIps());
        if (request.getPort() != null)
            config.setPort(request.getPort());
        if (request.getActive() != null)
            config.setActive(request.getActive());

        config = configRepository.save(config);
        return toResponse(config);
    }

    private PspReportConfigResponse toResponse(PspReportConfig config) {
        return PspReportConfigResponse.builder()
                .id(config.getId())
                .pspId(config.getPsp().getPspId())
                .pspName(config.getPsp().getPspCode())
                .reportUrl(config.getReportUrl())
                .allowedDomains(config.getAllowedDomains())
                .allowedIps(config.getAllowedIps())
                .port(config.getPort())
                .active(config.getActive())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
