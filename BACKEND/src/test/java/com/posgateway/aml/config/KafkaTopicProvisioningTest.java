package com.posgateway.aml.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kafka topics must actually be provisioned (configurable), covering every topic the code uses. */
class KafkaTopicProvisioningTest {

    private List<NewTopic> topics(KafkaConfig cfg) {
        return List.of(cfg.caseLifecycleTopic(), cfg.caseDecisionTopic(), cfg.transactionsRawTopic(),
                cfg.transactionsAuditTopic(), cfg.alertsGeneratedTopic());
    }

    @Test
    void everyDeclaredTopicHasABeanWithConfiguredSizing() {
        KafkaConfig cfg = new KafkaConfig();
        ReflectionTestUtils.setField(cfg, "replicationFactor", (short) 3);
        ReflectionTestUtils.setField(cfg, "transactionsRawPartitions", 24);

        List<NewTopic> topics = topics(cfg);
        assertThat(topics).extracting(NewTopic::name).containsExactlyInAnyOrderElementsOf(KafkaConfig.ALL_TOPICS);
        assertThat(topics).allSatisfy(t -> assertThat(t.replicationFactor()).isEqualTo((short) 3));
        assertThat(cfg.transactionsRawTopic().numPartitions()).isEqualTo(24);
    }

    @Test
    void everyTopicReferencedInSourceIsDeclared() throws IOException {
        Pattern ref = Pattern.compile("KafkaConfig\\.(TOPIC_[A-Z_]+)");
        Set<String> used = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = ref.matcher(Files.readString(f));
                while (m.find()) used.add(m.group(1));
            }
        }
        Set<String> declared = new HashSet<>();
        for (String name : List.of("TOPIC_CASE_LIFECYCLE", "TOPIC_CASE_DECISION", "TOPIC_TRANSACTIONS_RAW",
                "TOPIC_TRANSACTIONS_AUDIT", "TOPIC_ALERTS_GENERATED")) {
            declared.add(name);
        }
        assertThat(declared).containsAll(used);
    }

    @Test
    void productionAndDefaultProfilesProvisionTopics() throws IOException {
        for (String file : List.of("application.properties", "application-production.properties")) {
            String props = Files.readString(Path.of("src/main/resources", file));
            assertThat(props).contains("spring.kafka.admin.auto-create=${KAFKA_TOPICS_AUTO_CREATE:true}");
            assertThat(props).doesNotContain("spring.kafka.admin.auto-create=false");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryRunsUntilBrokerAcceptsThenStops() {
        KafkaAdmin admin = mock(KafkaAdmin.class);
        when(admin.initialize()).thenReturn(false, true);
        ObjectProvider<KafkaAdmin> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(admin);
        KafkaConfig.KafkaTopicProvisioningRetry retry = new KafkaConfig().kafkaTopicProvisioningRetry(provider, true);

        assertThat(retry.retryTopicProvisioning()).isFalse();
        assertThat(retry.retryTopicProvisioning()).isTrue();
        assertThat(retry.retryTopicProvisioning()).isTrue();
        verify(admin, times(2)).initialize();

        KafkaConfig.KafkaTopicProvisioningRetry disabled = new KafkaConfig().kafkaTopicProvisioningRetry(provider, false);
        assertThat(disabled.retryTopicProvisioning()).isFalse();
        verify(admin, times(2)).initialize();
    }
}
