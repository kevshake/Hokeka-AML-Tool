package com.posgateway.aml.service.reporting;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchemeReportingService {

    private final Map<String, ReportGenerator> generators;
    private final UserRepository userRepository;

    public SchemeReportingService(List<ReportGenerator> generatorList, UserRepository userRepository) {
        this.generators = generatorList.stream()
                .collect(Collectors.toMap(ReportGenerator::getType, Function.identity()));
        this.userRepository = userRepository;
    }

    /**
     * Generate Scheme Reporting Pack
     */
    public Map<String, Object> generateReport(String type, LocalDate startDate, LocalDate endDate) {
        // 1. Get Current User & PSP
        User currentUser = getCurrentUser();
        if (currentUser.getPsp() == null) {
            throw new AccessDeniedException("User is not associated with a PSP");
        }
        Long pspId = currentUser.getPsp().getPspId();

        // 2. Select Generator
        ReportGenerator generator = generators.get(type);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown report type: " + type);
        }

        // 3. Generate
        return generator.generate(pspId, startDate, endDate);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }
}
