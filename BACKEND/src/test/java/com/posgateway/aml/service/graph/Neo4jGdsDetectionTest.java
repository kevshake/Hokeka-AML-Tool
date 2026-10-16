package com.posgateway.aml.service.graph;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.data.redis.core.RedisTemplate;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Without the GDS plugin the service detects it once and never calls gds.* procedures. */
class Neo4jGdsDetectionTest {

    @SuppressWarnings("unchecked")
    private Neo4jGdsService serviceWithProcedureCount(Session session, long count) {
        Driver driver = mock(Driver.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        Result detection = mock(Result.class);
        Record row = mock(Record.class);
        Value c = Values.value(count);
        when(row.get("c")).thenReturn(c);
        when(detection.hasNext()).thenReturn(true);
        when(detection.next()).thenReturn(row);
        when(session.run(startsWith("SHOW PROCEDURES"))).thenReturn(detection);
        Result empty = mock(Result.class);
        when(empty.hasNext()).thenReturn(false);
        when(session.run(startsWith("MATCH"))).thenReturn(empty);
        when(session.run(anyString(), anyMap())).thenReturn(empty);
        return new Neo4jGdsService(driver, mock(RedisTemplate.class));
    }

    @Test
    void missingPluginIsDetectedOnceAndAlgorithmsAreSkipped() {
        Session session = mock(Session.class);
        Neo4jGdsService service = serviceWithProcedureCount(session, 0);

        service.refreshAllGraphMetrics();
        service.refreshAllGraphMetrics();
        service.runPageRank();

        assertThat(service.isGdsAvailable()).isFalse();
        verify(session, times(1)).run(startsWith("SHOW PROCEDURES"));
        verify(session, never()).run(startsWith("CALL gds"), anyMap());
    }

    @Test
    void installedPluginRunsTheAlgorithms() {
        Session session = mock(Session.class);
        Neo4jGdsService service = serviceWithProcedureCount(session, 1);

        service.refreshAllGraphMetrics();

        assertThat(service.isGdsAvailable()).isTrue();
        verify(session).run(startsWith("CALL gds.graph.drop"), anyMap());
        verify(session).run(org.mockito.ArgumentMatchers.contains("gds.pageRank.write"), anyMap());
    }

    @Test
    void unreachableNeo4jIsNotCachedAsMissing() {
        Driver driver = mock(Driver.class);
        when(driver.session(any(SessionConfig.class))).thenThrow(new RuntimeException("connection refused"));
        @SuppressWarnings("unchecked")
        Neo4jGdsService service = new Neo4jGdsService(driver, mock(RedisTemplate.class));

        assertThat(service.isGdsAvailable()).isFalse();
        assertThat(service.isGdsAvailable()).isFalse();
        verify(driver, times(2)).session(any(SessionConfig.class));
    }
}
