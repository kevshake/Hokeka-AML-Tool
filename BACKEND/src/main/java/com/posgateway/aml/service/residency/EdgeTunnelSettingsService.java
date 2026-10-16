package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-PSP Edge Tunnel settings exactly as distributed in the sealed Edge config document (contract
 * §6.1 {@code tunnel} section), together with the document's {@code configVersion}. The tunnel
 * server uses this to compute {@code hello.ack.opsEffective} and the in-flight cap, so the cloud
 * side never allows more than the PSP's Edges were told.
 */
@Service
public class EdgeTunnelSettingsService {

    private final EdgeConfigService configService;

    public EdgeTunnelSettingsService(EdgeConfigService configService) {
        this.configService = configService;
    }

    /**
     * Tunnel section of a PSP's current config document.
     *
     * @param enabled               cloud-side tunnel switch
     * @param opsAllowed            read ops the cloud may send (plus ledger.backfill when configured)
     * @param backfillAllowed       whether the one-off legacy backfill may run
     * @param maxInflightPerSession requests in flight per session (1..16)
     * @param configVersion         version of the config document these values come from
     */
    public record TunnelSettings(boolean enabled, List<String> opsAllowed, boolean backfillAllowed,
                                 int maxInflightPerSession, long configVersion) {}

    /** The PSP's tunnel settings from its current (versioned) Edge config document. */
    public TunnelSettings settingsFor(Long pspId) {
        EdgeConfigService.IssuedConfig issued = configService.current(pspId);
        JsonNode tunnel = issued.document().get("tunnel");
        List<String> ops = new ArrayList<>();
        tunnel.get("opsAllowed").forEach(op -> ops.add(op.asText()));
        return new TunnelSettings(
                tunnel.get("enabled").asBoolean(),
                List.copyOf(ops),
                tunnel.get("backfillAllowed").asBoolean(),
                tunnel.get("maxInflightPerSession").asInt(),
                issued.version());
    }
}
