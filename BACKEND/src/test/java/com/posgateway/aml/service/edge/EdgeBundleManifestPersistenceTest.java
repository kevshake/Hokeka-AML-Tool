package com.posgateway.aml.service.edge;

import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.edge.EdgeBundleManifestRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression (sim run 2026-10-16): every first {@code GET /edge/bundle} answered 500 because
 * {@code sealFor} ran in a read-only transaction while recording the bundle manifest
 * ("cannot execute INSERT in a read-only transaction"), so edges never armed a bundle.
 */
class EdgeBundleManifestPersistenceTest {

    @Test
    void sealForRunsInAReadWriteTransaction() throws Exception {
        Transactional tx = EdgeBundleDistributionService.class
                .getMethod("sealFor", EdgeNode.class).getAnnotation(Transactional.class);
        assertThat(tx).as("sealFor must be transactional").isNotNull();
        assertThat(tx.readOnly()).as("sealFor writes edge_bundles").isFalse();
    }

    @Test
    void sealingRecordsTheManifestWithTheIdempotentInsert() throws Exception {
        RuleDefinitionRepository rules = mock(RuleDefinitionRepository.class);
        RuleDefinition rule = new RuleDefinition();
        rule.setId(7L);
        rule.setName("High value");
        rule.setDescription("High value");
        rule.setAction("HOLD");
        rule.setScore(40);
        rule.setPriority(5);
        rule.setRuleType("JSON");
        rule.setRuleJson("{\"conditions\":{\"field\":\"amount\",\"operator\":\">\",\"value\":100}}");
        rule.setEnabled(true);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        when(rules.findByEnabledTrueAndPspIdOrderByPriorityDesc(anyLong())).thenReturn(List.of(rule));

        EdgeBundleManifestRepository manifests = mock(EdgeBundleManifestRepository.class);
        when(manifests.findByPspIdAndManifestHash(anyLong(), anyString())).thenReturn(Optional.empty());

        EdgeBundleService bundleService = new EdgeBundleService();
        EdgeBundleDistributionService distribution = new EdgeBundleDistributionService(rules,
                new EdgeRuleCompiler(bundleService), bundleService,
                new EdgeControlPlaneKeys(new EdgeProperties()), new HokekaSecureEnvelope(), manifests);

        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        EdgeNode node = new EdgeNode();
        node.setId(1L);
        node.setPspId(42L);
        node.setEdgeId("edge-1");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public(
                        (java.security.interfaces.XECPublicKey) edgeX.getPublic())));

        distribution.sealFor(node);

        verify(manifests).insertIfAbsent(eq(42L), anyString(), contains("\"ruleId\":7"), eq(1),
                any(LocalDateTime.class));
        verify(manifests, never()).save(any());
        verify(manifests, never()).insertIfAbsent(eq(42L), anyString(), anyString(), anyInt(), eq(null));
    }
}
