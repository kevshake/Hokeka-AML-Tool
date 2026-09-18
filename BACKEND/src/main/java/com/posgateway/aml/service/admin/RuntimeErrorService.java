package com.posgateway.aml.service.admin;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.admin.RuntimeError;
import com.posgateway.aml.repository.admin.RuntimeErrorRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class RuntimeErrorService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_STACK_LENGTH = 16000;

    private final RuntimeErrorRepository runtimeErrorRepository;
    private final PspIsolationService pspIsolationService;

    public RuntimeErrorService(RuntimeErrorRepository runtimeErrorRepository,
                               PspIsolationService pspIsolationService) {
        this.runtimeErrorRepository = runtimeErrorRepository;
        this.pspIsolationService = pspIsolationService;
    }

    @Transactional
    public void record(String errorCode, String message, Throwable cause) {
        RuntimeError row = new RuntimeError();
        row.setErrorCode(errorCode);
        row.setMessage(truncate(message, MAX_MESSAGE_LENGTH));
        row.setStackTrace(truncate(stackTraceOf(cause), MAX_STACK_LENGTH));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            row.setUserId(user.getId());
            if (user.getPsp() != null) {
                row.setPspId(user.getPsp().getPspId());
            }
        }

        runtimeErrorRepository.save(row);
    }

    @Transactional(readOnly = true)
    public Page<RuntimeError> list(Pageable pageable) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return runtimeErrorRepository.findAllByOrderByOccurredAtDesc(pageable);
        }
        Long pspId = pspIsolationService.getCurrentUserPspId();
        if (pspId != null && pspId > 0) {
            return runtimeErrorRepository.findByPspIdOrderByOccurredAtDesc(pspId, pageable);
        }
        return runtimeErrorRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    private static String stackTraceOf(Throwable cause) {
        if (cause == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
