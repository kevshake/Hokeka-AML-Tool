package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.ledger.LedgerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The feature store bean scopes velocity to the Edge Site: {@code EDGE_SITE_ID} when set, else the
 * node's {@code EDGE_EDGEID} (single-node site), and no store at all when neither is set.
 */
class FeatureStoreConfigTest {

    /** Builds the bean with a connected client and the given site / edge ids. */
    private EdgeFeatureStore build(String siteId, String edgeId) {
        FeatureStoreConfig config = new FeatureStoreConfig();
        ReflectionTestUtils.setField(config, "namespace", "hokeka");
        @SuppressWarnings("unchecked")
        ObjectProvider<AerospikeClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(AerospikeClient.class));
        LedgerProperties ledger = new LedgerProperties();
        ledger.setSiteId(siteId);
        ControlPlaneProperties controlPlane = new ControlPlaneProperties();
        controlPlane.setEdgeId(edgeId);
        return config.featureStore(provider, ledger, controlPlane);
    }

    @Test
    void configuredSiteIdScopesTheVelocityKeys() {
        AerospikeFeatureStore store = assertInstanceOf(AerospikeFeatureStore.class, build("nbo-dc1", "acme-eu-1"));
        assertEquals("nbo-dc1", store.siteId());
    }

    @Test
    void singleNodeSiteFallsBackToTheEdgeId() {
        AerospikeFeatureStore store = assertInstanceOf(AerospikeFeatureStore.class, build("", "acme-eu-1"));
        assertEquals("acme-eu-1", store.siteId());
    }

    @Test
    void noSiteAndNoEdgeIdMeansNoLocalHistory() {
        assertInstanceOf(NoOpFeatureStore.class, build("", ""));
    }
}
