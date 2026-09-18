package com.hokeka.edge;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads a plaintext IR bundle straight into the engine — <b>dev profile only</b>.
 *
 * <p>In production the only way a bundle enters the engine is the signed, encrypted, replay-guarded
 * control-plane pull ({@code RuleBundlePoller}); an unauthenticated plaintext write path would let
 * anyone who can reach the API replace the rules. Outside the {@code dev} profile this bean does not
 * exist and {@code POST /edge/bundle} is a 404.
 */
@RestController
@RequestMapping("/edge")
@Profile("dev")
public class DevBundleController {

    private final EdgeEngine engine;

    public DevBundleController(EdgeEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/bundle")
    public Map<String, Object> loadBundle(@RequestBody String bundleJson) {
        long version = engine.loadPlainBundle(bundleJson.getBytes(StandardCharsets.UTF_8));
        return Map.of("loaded", true, "version", version);
    }
}
