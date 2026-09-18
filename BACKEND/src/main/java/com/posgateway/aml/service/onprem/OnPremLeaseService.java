package com.posgateway.aml.service.onprem;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremInstanceCreateRequest;
import com.posgateway.aml.dto.onprem.OnPremInstanceCreatedResponse;
import com.posgateway.aml.dto.onprem.OnPremInstanceView;
import com.posgateway.aml.dto.onprem.OnPremLeaseRequest;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import com.posgateway.aml.entity.onprem.OnPremInstance;
import com.posgateway.aml.entity.onprem.OnPremInstanceStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.onprem.OnPremInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Central Hokeka service: register on-prem instances, issue/renew multi-day leases, revoke.
 */
@Service
public class OnPremLeaseService {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnPremInstanceRepository repository;
    private final PspRepository pspRepository;
    private final PasswordEncoder passwordEncoder;
    private final OnPremLeaseTokenService tokenService;
    private final OnPremCheckInScheduler checkInScheduler;
    private final HokekaAuthProperties properties;

    public OnPremLeaseService(OnPremInstanceRepository repository,
                              PspRepository pspRepository,
                              PasswordEncoder passwordEncoder,
                              OnPremLeaseTokenService tokenService,
                              OnPremCheckInScheduler checkInScheduler,
                              HokekaAuthProperties properties) {
        this.repository = repository;
        this.pspRepository = pspRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.checkInScheduler = checkInScheduler;
        this.properties = properties;
    }

    @Transactional
    public OnPremInstanceCreatedResponse createInstance(OnPremInstanceCreateRequest request,
                                                        String createdBy) {
        Psp psp = pspRepository.findById(request.pspId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PSP not found"));
        if (!"ACTIVE".equalsIgnoreCase(psp.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PSP must be ACTIVE before on-prem instances can be registered");
        }
        if (repository.existsByInstanceId(request.instanceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "instanceId already registered");
        }

        String clientId = "op_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = generateSecret();
        int approvedDays = request.approvedDays() != null ? request.approvedDays() : 7;

        OnPremInstance entity = new OnPremInstance();
        entity.setPspId(psp.getPspId());
        entity.setInstanceId(request.instanceId().trim());
        entity.setClientId(clientId);
        entity.setClientSecretHash(passwordEncoder.encode(clientSecret));
        entity.setDisplayName(request.displayName().trim());
        entity.setStatus(OnPremInstanceStatus.ACTIVE);
        entity.setApprovedDays(approvedDays);
        entity.setCreatedBy(createdBy);

        OnPremInstance saved = repository.save(entity);
        log.info("Registered on-prem instance {} for PSP {} (clientId={})",
                saved.getInstanceId(), saved.getPspId(), saved.getClientId());
        return new OnPremInstanceCreatedResponse(toView(saved), clientSecret);
    }

    @Transactional(readOnly = true)
    public List<OnPremInstanceView> listAll() {
        return repository.findAll().stream().map(OnPremLeaseService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<OnPremInstanceView> listByPsp(Long pspId) {
        return repository.findByPspId(pspId).stream().map(OnPremLeaseService::toView).toList();
    }

    @Transactional(readOnly = true)
    public OnPremInstanceView get(Long id) {
        return toView(require(id));
    }

    @Transactional
    public OnPremInstanceView setApprovedDays(Long id, int approvedDays) {
        if (approvedDays < 1 || approvedDays > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approvedDays must be 1..365");
        }
        OnPremInstance entity = require(id);
        entity.setApprovedDays(approvedDays);
        return toView(repository.save(entity));
    }

    @Transactional
    public OnPremInstanceView revoke(Long id, String revokedBy) {
        OnPremInstance entity = require(id);
        entity.setStatus(OnPremInstanceStatus.REVOKED);
        entity.setRevokedAt(Instant.now());
        entity.setRevokedBy(revokedBy);
        entity.setLeaseUntil(Instant.now());
        entity.setNextCheckAt(Instant.now());
        log.warn("Revoked on-prem instance {} by {}", entity.getInstanceId(), revokedBy);
        return toView(repository.save(entity));
    }

    @Transactional
    public OnPremInstanceView suspend(Long id, String actor) {
        OnPremInstance entity = require(id);
        entity.setStatus(OnPremInstanceStatus.SUSPENDED);
        entity.setLeaseUntil(Instant.now());
        entity.setNextCheckAt(Instant.now());
        log.warn("Suspended on-prem instance {} by {}", entity.getInstanceId(), actor);
        return toView(repository.save(entity));
    }

    /**
     * Authenticate client credentials and grant (or renew) a multi-day lease with a
     * server-assigned next check-in time.
     */
    @Transactional
    public OnPremLeaseResponse authenticateAndGrant(OnPremLeaseRequest request) {
        OnPremInstance entity = repository.findByClientId(request.clientId())
                .orElseThrow(() -> unauthorized("Invalid client credentials"));

        if (!passwordEncoder.matches(request.clientSecret(), entity.getClientSecretHash())) {
            throw unauthorized("Invalid client credentials");
        }
        if (!entity.getInstanceId().equals(request.instanceId())) {
            throw unauthorized("instanceId does not match registered credentials");
        }
        if (!entity.canReceiveLease()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Instance is " + entity.getStatus() + " and cannot receive a lease");
        }

        Psp psp = pspRepository.findById(entity.getPspId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "PSP missing"));
        if (!"ACTIVE".equalsIgnoreCase(psp.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owning PSP is not ACTIVE");
        }

        Instant now = Instant.now();
        int approvedDays = entity.getApprovedDays() != null ? entity.getApprovedDays() : 7;
        int checkIntervalDays = Math.max(1, properties.getCheckIntervalDays());
        Instant validUntil = now.plus(approvedDays, ChronoUnit.DAYS);
        Instant nextCheckAt = checkInScheduler.assignNextCheckAt(
                entity.getInstanceId(), now, checkIntervalDays, validUntil);
        String jti = UUID.randomUUID().toString().replace("-", "");

        String token = tokenService.issueToken(
                entity.getInstanceId(), entity.getPspId(), validUntil, nextCheckAt, approvedDays, jti);

        entity.setLeaseUntil(validUntil);
        entity.setNextCheckAt(nextCheckAt);
        entity.setLastLeaseJti(jti);
        entity.setLastSeenAt(now);
        if (request.hostname() != null && !request.hostname().isBlank()) {
            entity.setHostname(request.hostname().trim());
        }
        if (request.agentVersion() != null && !request.agentVersion().isBlank()) {
            entity.setAgentVersion(request.agentVersion().trim());
        }
        repository.save(entity);

        log.info("Granted on-prem lease instanceId={} pspId={} validUntil={} nextCheckAt={}",
                entity.getInstanceId(), entity.getPspId(), validUntil, nextCheckAt);

        return new OnPremLeaseResponse(
                entity.getInstanceId(),
                entity.getPspId(),
                validUntil,
                nextCheckAt,
                approvedDays,
                checkIntervalDays,
                token,
                "HokekaLease",
                now.getEpochSecond());
    }

    private OnPremInstance require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instance not found"));
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static OnPremInstanceView toView(OnPremInstance e) {
        return new OnPremInstanceView(
                e.getId(),
                e.getPspId(),
                e.getInstanceId(),
                e.getClientId(),
                e.getDisplayName(),
                e.getStatus(),
                e.getApprovedDays(),
                e.getLeaseUntil(),
                e.getNextCheckAt(),
                e.getLastSeenAt(),
                e.getHostname(),
                e.getAgentVersion(),
                e.getRevokedAt(),
                e.getRevokedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
