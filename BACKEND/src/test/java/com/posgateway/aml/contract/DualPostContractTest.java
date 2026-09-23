package com.posgateway.aml.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract guard: edge evaluation must not silently create cloud compliance artifacts.
 * Integrators dual-post per {@code docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md}.
 */
class DualPostContractTest {

    @Test
    void edgeEvaluateDoesNotReferenceCloudIngestOrAlertCreation() {
        String body = readSource("edge-host/src/main/java/com/hokeka/edge/EdgeController.java");
        assertTrue(body.contains("/edge/evaluate") || body.contains("@PostMapping(\"/evaluate\")"),
                "edge evaluate endpoint must exist");
        assertFalse(body.contains("transactions/ingest"),
                "edge controller must not call cloud ingest — dual-post is integrator responsibility");
        assertFalse(body.contains("createAlert"),
                "edge controller must not create cloud alerts");
    }

    @Test
    void edgeDistributionBundlePathIsSeparateFromIngest() {
        String body = readSource(
                "BACKEND/src/main/java/com/posgateway/aml/controller/edge/EdgeDistributionController.java");
        assertTrue(body.contains("/edge/bundle") || body.contains("bundle"),
                "edge distribution must expose bundle pull");
        assertFalse(body.contains("POST /transactions/ingest"),
                "edge distribution channel must not substitute for cloud ingest");
    }

    @Test
    void dualPostContractDocExistsAndNamesBothPaths() {
        String doc = readSource("docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md");
        assertTrue(doc.contains("/edge/evaluate"));
        assertTrue(doc.contains("/api/v1/transactions/ingest"));
        assertTrue(doc.contains("dual-post"));
    }

    private static String readSource(String relativePath) {
        try {
            java.nio.file.Path cwd = java.nio.file.Path.of(System.getProperty("user.dir"));
            java.nio.file.Path[] candidates = {
                    cwd.resolve(relativePath),
                    cwd.getParent().resolve(relativePath),
                    java.nio.file.Path.of("/workspace").resolve(relativePath)
            };
            for (java.nio.file.Path path : candidates) {
                if (java.nio.file.Files.isRegularFile(path)) {
                    return java.nio.file.Files.readString(path);
                }
            }
            throw new AssertionError("Could not find " + relativePath + " from " + cwd);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Could not read " + relativePath, e);
        }
    }
}
