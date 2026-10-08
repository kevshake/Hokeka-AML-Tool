package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.entity.rules.RuleLifecycleStatus;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Builds and HSE-1 seals the rule bundle for one authorised edge node.
 *
 * <p>Flow (contract §5, platform doc §5): the PSP's enabled rules are compiled to the edge Rule IR,
 * versioned, serialised, then sealed with {@link com.posgateway.aml.edge.crypto.HokekaSecureEnvelope}
 * against <b>that edge's pinned X25519 public key</b> and signed with the control plane's Ed25519
 * key, under the fixed context {@value #BUNDLE_CONTEXT}. Because the context is bound into both the
 * HKDF info and the AEAD AAD, a bundle envelope can never be opened as a metrics envelope, and
 * because the sealing key is the edge's own pinned key, no other edge (and nothing in between, e.g.
 * a PSP-controlled TLS-terminating proxy) can read it.
 */
@Service
public class EdgeBundleDistributionService {

    private static final Logger log = LoggerFactory.getLogger(EdgeBundleDistributionService.class);

    /** Contract §3 — rule bundle distribution context bytes. */
    public static final String BUNDLE_CONTEXT = "hokeka.rules.bundle";

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final EdgeRuleCompiler compiler;
    private final EdgeBundleService bundleService;
    private final EdgeControlPlaneKeys controlPlaneKeys;
    private final HokekaSecureEnvelope envelope;
    private final ObjectMapper mapper = new ObjectMapper();

    public EdgeBundleDistributionService(RuleDefinitionRepository ruleDefinitionRepository,
                                         EdgeRuleCompiler compiler,
                                         EdgeBundleService bundleService,
                                         EdgeControlPlaneKeys controlPlaneKeys,
                                         HokekaSecureEnvelope envelope) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.compiler = compiler;
        this.bundleService = bundleService;
        this.controlPlaneKeys = controlPlaneKeys;
        this.envelope = envelope;
    }

    /** @param version the bundle version, also used as the HTTP ETag for {@code If-None-Match}. */
    public record SealedBundle(long version, byte[] sealed, int ruleCount, List<String> skippedRules) {}

    /** The version an edge would be served right now, without doing the (costlier) sealing work. */
    @Transactional(readOnly = true)
    public long currentVersion(Long pspId) {
        return compiler.compile(rulesFor(pspId)).version();
    }

    /**
     * Compile + seal the current bundle for {@code node}. The node must already have been
     * authorised by {@link EdgeEnrollmentService#findAuthorized(String)} — this method assumes the
     * keys are pinned and does not re-check the lifecycle state.
     *
     * <p>The rule IR is wrapped in the contract §4 <b>replay envelope</b> before sealing, because
     * the channel is symmetric: the edge's {@code SealedEnvelopeCodec.openPayloadBytes} opens the
     * HSE-1 envelope, validates {@code nonce}/{@code issuedAt}/{@code edgeId} against its own
     * replay guard, and only then hands the inner {@code payload} to the interpreter. Sealing the
     * bare IR would be rejected by every edge as "replay envelope has no payload".
     */
    /**
     * Thrown instead of sealing a bundle that would contain no rules. Callers must translate this to
     * a non-200 response so the edge keeps whatever bundle it already has (or stays fail-closed if it
     * has none) rather than being handed a permissive one.
     */
    public static class EmptyBundleException extends IllegalStateException {
        public EmptyBundleException(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public SealedBundle sealFor(EdgeNode node) {
        EdgeRuleCompiler.CompilationResult compiled = compiler.compile(rulesFor(node.getPspId()));

        // FAIL-CLOSED: both edge interpreters start at ALLOW and iterate the rule list, so a
        // zero-rule bundle is a signed instruction to allow everything. That can happen silently when
        // every rule fails to compile (e.g. none carry structured rule_json). Refuse to seal it —
        // an edge must never be armed with a permissive bundle.
        if (compiled.rules().isEmpty()) {
            log.error("Refusing to seal an EMPTY rule bundle for edge {} (psp {}): {} rule(s) were "
                            + "skipped as uncompilable {}. The edge keeps its previous bundle.",
                    node.getEdgeId(), node.getPspId(), compiled.skipped().size(), compiled.skipped());
            throw new EmptyBundleException(
                    "refusing to distribute a zero-rule bundle (would allow all traffic); "
                            + compiled.skipped().size() + " rule(s) failed to compile");
        }

        byte[] plaintext = wrapInReplayEnvelope(
                bundleService.buildBundleJson(compiled.version(), node.getPspId(), compiled.rules()),
                node.getEdgeId());

        byte[] edgeX25519Pub = Base64.getDecoder().decode(node.getEdgeX25519PublicKey());
        byte[] sealed = envelope.seal(plaintext, edgeX25519Pub, controlPlaneKeys.signingPrivateKey(),
                BUNDLE_CONTEXT.getBytes(StandardCharsets.UTF_8));

        log.debug("Sealed edge bundle v{} for edge {} (psp {}): {} rules, {} skipped",
                compiled.version(), node.getEdgeId(), node.getPspId(), compiled.rules().size(),
                compiled.skipped().size());
        return new SealedBundle(compiled.version(), sealed, compiled.rules().size(), compiled.skipped());
    }

    /** {@code {"nonce":…,"issuedAt":…,"edgeId":…,"payload":<rule IR>}} — contract §4. */
    private byte[] wrapInReplayEnvelope(byte[] payloadJson, String edgeId) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("nonce", EdgeReplayGuard.newNonce());
            root.put("issuedAt", Instant.now().toString());
            root.put("edgeId", edgeId);
            root.set("payload", mapper.readTree(payloadJson));
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build the bundle replay envelope", e);
        }
    }

    /**
     * A PSP's own enabled rules. Only when the PSP has NO rules provisioned at all (a fresh tenant,
     * no per-PSP copies yet) do we fall back to the enabled global system defaults, so a newly
     * onboarded edge still evaluates something meaningful.
     *
     * <p>Falling back on "no enabled rules" alone would silently re-arm the global defaults on an
     * edge whose PSP had deliberately disabled its entire rule set — distributing rules the operator
     * explicitly turned off. That case now yields an empty set, which {@link #sealFor} refuses to
     * seal, so the edge keeps its previous bundle instead of being handed unexpected rules.
     */
    private List<RuleDefinition> rulesFor(Long pspId) {
        List<RuleDefinition> own = live(ruleDefinitionRepository
                .findByEnabledTrueAndPspIdOrderByPriorityDesc(pspId));
        if (!own.isEmpty()) {
            return own;
        }
        if (pspId != null && ruleDefinitionRepository.existsByPspId(pspId)) {
            return List.of(); // provisioned but deliberately all-disabled — do not substitute defaults
        }
        return live(ruleDefinitionRepository.findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc());
    }

    /**
     * Only a rule that has passed maker/checker is distributed. {@code APPROVED} covers a
     * future-dated change whose previous live content is still the one in force. Drafts, pending
     * creates, rejections and retirements stay off the edge even if {@code enabled} was left set.
     */
    private static List<RuleDefinition> live(List<RuleDefinition> rules) {
        return rules.stream().filter(rule -> {
            RuleLifecycleStatus status = rule.getLifecycleStatus();
            return status == null
                    || status == RuleLifecycleStatus.ACTIVE
                    || status == RuleLifecycleStatus.APPROVED;
        }).toList();
    }
}
