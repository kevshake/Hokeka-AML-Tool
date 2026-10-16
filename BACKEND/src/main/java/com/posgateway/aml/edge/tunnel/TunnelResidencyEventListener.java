package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.residency.PspDataSettingChange;
import com.posgateway.aml.service.residency.EdgeConfigUpdatedEvent;
import com.posgateway.aml.service.residency.PspDataSettingsChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Connects work package R3's residency events to the Edge Tunnel (contract §6.2, §7.9, §7.10, §9).
 *
 * <ul>
 *   <li>{@link EdgeConfigUpdatedEvent}: every Control Plane instance re-applies the new
 *       {@code tunnel} section to the sessions it holds (relay broadcast {@code CONFIG_CHANGED}:
 *       disabled → close 4403, ops withdrawn → removed from {@code opsEffective} at once), then
 *       {@code notify config.updated} goes to every live session of the PSP on any instance.</li>
 *   <li>{@link PspDataSettingsChangedEvent} for {@code SUPPORT_ACCESS}: when the flag is now off,
 *       every instance cancels its in-flight {@code PLATFORM_USER} pulls of the PSP
 *       ({@code cancel SUPPORT_ACCESS_REVOKED}).</li>
 * </ul>
 *
 * <p>Both run after the publishing transaction commits (or at once when published outside one),
 * so the tunnel never acts on a change that is rolled back, and a new pull started after the
 * cancel already reads the committed flag. A failure here is logged: the Edge still learns the
 * new config on its next poll, and its own §9 re-check refuses platform requests once that config
 * has support access off.
 */
public class TunnelResidencyEventListener {

    private static final Logger log = LoggerFactory.getLogger(TunnelResidencyEventListener.class);

    private final TunnelNotifier notifier;
    private final TunnelMessaging messaging;
    private final EdgeDataGateway gateway;
    private final SupportAccessPolicy supportAccess;

    /** Wires the listener to the tunnel's notifier, relay, gateway and support-access policy. */
    public TunnelResidencyEventListener(TunnelNotifier notifier, TunnelMessaging messaging, EdgeDataGateway gateway,
                                        SupportAccessPolicy supportAccess) {
        this.notifier = notifier;
        this.messaging = messaging;
        this.gateway = gateway;
        this.supportAccess = supportAccess;
    }

    /** A new config version was issued: enforce its tunnel section, then tell the PSP's Edges. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigUpdated(EdgeConfigUpdatedEvent event) {
        if (event.pspId() == null || event.configVersion() < 1) {
            log.warn("Ignoring Edge config update without a PSP or a valid version");
            return;
        }
        long pspId = event.pspId();
        try {
            messaging.broadcast(RelayMessage.configChanged(pspId));
        } catch (RuntimeException e) {
            log.warn("Could not broadcast the tunnel config change of PSP {}: {}", pspId, e.getClass().getSimpleName());
        }
        try {
            List<TunnelNotifier.Delivery> sent = notifier.notifyPsp(pspId, configUpdatedBody(event.configVersion()));
            log.info("Sent notify config.updated (version {}) to {} tunnel session(s) of PSP {}",
                    event.configVersion(), sent.size(), pspId);
        } catch (RuntimeException e) {
            log.warn("Could not send notify config.updated to PSP {}: {}", pspId, e.getClass().getSimpleName());
        }
    }

    /** Support access changed: once it is off, cut every in-flight platform pull of the PSP. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSettingsChanged(PspDataSettingsChangedEvent event) {
        if (event.pspId() == null || !PspDataSettingChange.Setting.SUPPORT_ACCESS.name().equals(event.reason())) {
            return;
        }
        long pspId = event.pspId();
        boolean enabled;
        try {
            enabled = supportAccess.isSupportAccessEnabled(pspId);
        } catch (RuntimeException e) {
            // Cannot confirm the flag: revoke anyway. Cancelling platform pulls is always safe.
            log.warn("Could not read support access of PSP {} ({}); revoking in-flight platform pulls",
                    pspId, e.getClass().getSimpleName());
            enabled = false;
        }
        if (enabled) {
            return;
        }
        try {
            gateway.revokeSupportAccess(pspId);
            log.info("Support access of PSP {} withdrawn: in-flight platform pulls cancelled on every instance", pspId);
        } catch (RuntimeException e) {
            // The relay is unreachable: still cut this instance's pulls; other instances' pulls end
            // at their deadline and the Edge refuses new ones after its next config poll.
            log.warn("Could not broadcast the support-access withdrawal of PSP {} ({}); cancelling local pulls",
                    pspId, e.getClass().getSimpleName());
            gateway.onBroadcast(RelayMessage.supportRevoked(pspId));
        }
    }

    /** {@code tunnel/notify/config.updated.schema.json}: {@code {type, configVersion}}. */
    static ObjectNode configUpdatedBody(long configVersion) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("type", "config.updated");
        body.put("configVersion", configVersion);
        return body;
    }
}
