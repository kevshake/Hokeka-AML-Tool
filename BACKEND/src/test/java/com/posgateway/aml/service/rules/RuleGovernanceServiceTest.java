package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.entity.rules.RuleLifecycleStatus;
import com.posgateway.aml.entity.rules.RuleVersion;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.repository.rules.RuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuleGovernanceServiceTest {

    private final RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository versionRepository = mock(RuleVersionRepository.class);
    private final DroolsRulesService droolsRulesService = mock(DroolsRulesService.class);
    private final AtomicReference<RuleVersion> storedVersion = new AtomicReference<>();
    private RuleGovernanceService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new RuleGovernanceService(ruleRepository, versionRepository, droolsRulesService,
                objectMapper, new SpelRuleExecutor(objectMapper));
        when(ruleRepository.findByNameAndPspId(anyString(), org.mockito.ArgumentMatchers.nullable(Long.class)))
                .thenReturn(Optional.empty());
        when(ruleRepository.save(any(RuleDefinition.class))).thenAnswer(invocation -> {
            RuleDefinition rule = invocation.getArgument(0);
            if (rule.getId() == null) ReflectionTestUtils.setField(rule, "id", 42L);
            return rule;
        });
        when(versionRepository.findFirstByRuleIdOrderByVersionNumberDesc(anyLong()))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(RuleVersion.class))).thenAnswer(invocation -> {
            RuleVersion version = invocation.getArgument(0);
            if (version.getId() == null) ReflectionTestUtils.setField(version, "id", 7L);
            ReflectionTestUtils.invokeMethod(version, "onCreate");
            storedVersion.set(version);
            return version;
        });
        when(versionRepository.findById(7L)).thenAnswer(invocation -> Optional.ofNullable(storedVersion.get()));
    }

    @Test
    void proposedEnabledRuleStaysInactiveUntilIndependentApproval() {
        User maker = user(10L, "maker");
        User reviewer = user(11L, "reviewer");
        RuleDefinition proposed = rule("Large cash deposit", true);

        RuleDefinition pending = service.proposeCreate(proposed, maker, null, "Add cash typology");

        assertThat(pending.isEnabled()).isFalse();
        assertThat(pending.getLifecycleStatus()).isEqualTo(RuleLifecycleStatus.PENDING_APPROVAL);
        assertThat(pending.getPendingVersionId()).isEqualTo(7L);
        verify(droolsRulesService, never()).reloadRules();

        service.approve(7L, reviewer, null, "Validated expression and test evidence");

        assertThat(pending.isEnabled()).isTrue();
        assertThat(pending.getLifecycleStatus()).isEqualTo(RuleLifecycleStatus.ACTIVE);
        assertThat(pending.getCurrentVersionNumber()).isEqualTo(1);
        assertThat(pending.getPendingVersionId()).isNull();
        verify(droolsRulesService).reloadRules();
    }

    @Test
    void makerCannotApproveOwnRuleVersion() {
        User maker = user(10L, "maker");
        service.proposeCreate(rule("Rapid movement", true), maker, null, "New typology");

        assertThatThrownBy(() -> service.approve(7L, maker, null, "Self approval"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("cannot approve");
        verify(droolsRulesService, never()).reloadRules();
    }

    @Test
    void approvalRequiresDocumentedReason() {
        service.proposeCreate(rule("Dormant account", false), user(10L, "maker"), null, "New typology");

        assertThatThrownBy(() -> service.approve(7L, user(11L, "reviewer"), null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void clientSuppliedIdentityAndProvenanceFieldsAreStrippedOnCreate() {
        // The API binds the JPA entity straight from the request body. A caller must not be able to
        // set `id` (which would turn save() into an UPDATE of an arbitrary existing rule), claim
        // systemManaged (undeletable + identity-locked), forge catalogue provenance, or inject
        // version pointers that corrupt the maker/checker audit chain.
        RuleDefinition hostile = rule("Hostile", true);
        hostile.setId(999L);
        hostile.setSystemManaged(true);
        hostile.setExternalCode("R-2");
        hostile.setDerivedFromRuleId(123L);
        hostile.setCurrentVersionId(456L);
        hostile.setPendingVersionId(789L);

        RuleDefinition saved = service.proposeCreate(hostile, user(10L, "maker"), 5L, "Create");

        assertThat(saved.getId()).isEqualTo(42L); // assigned by the repository, not the client
        assertThat(saved.isSystemManaged()).isFalse();
        assertThat(saved.getExternalCode()).isNull();
        assertThat(saved.getDerivedFromRuleId()).isNull();
        assertThat(saved.getCurrentVersionId()).isNull();
        assertThat(saved.getPspId()).isEqualTo(5L); // ownership assigned server-side
    }

    @Test
    void editingARuleDoesNotSilentlyReEnableIt() {
        // `enabled` is a primitive defaulting to true, so a PUT body that omits it deserialises to
        // true. Editing (say) a description must never re-arm a deliberately disabled rule —
        // enablement changes go through the dedicated enable/disable endpoints.
        RuleDefinition existing = rule("Disabled rule", false);
        ReflectionTestUtils.setField(existing, "id", 77L);
        existing.setEnabled(false);

        RuleDefinition patch = new RuleDefinition(); // enabled defaults to TRUE
        patch.setDescription("Just fixing a typo");

        service.proposeUpdate(existing, patch, user(10L, "maker"), "Edit description");

        RuleVersion version = storedVersion.get();
        assertThat(version).isNotNull();
        assertThat(version.getSnapshot())
                .as("an edit must preserve the rule's disabled state, not re-arm it")
                .containsEntry("enabled", false);
    }

    private RuleDefinition rule(String name, boolean enabled) {
        RuleDefinition rule = new RuleDefinition();
        rule.setName(name);
        rule.setDescription("Test rule");
        rule.setRuleType("SPEL");
        rule.setRuleExpression("amount > 10000");
        rule.setAction("ALERT");
        rule.setPriority(100);
        rule.setEnabled(enabled);
        return rule;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
