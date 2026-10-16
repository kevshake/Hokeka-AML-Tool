package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One message between Control Plane instances on Redis pub/sub (§7.12 cross-instance routing).
 *
 * <p>{@link #frame} is always a complete <b>sealed</b> outer tunnel frame (or absent): the
 * session-holding instance forwards the Edge's sealed {@code response}/{@code chunk}/{@code error}
 * bytes unopened, and cloud→Edge frames are sealed by the origin before publishing, so Redis never
 * sees plaintext. The other fields are routing metadata (ids, the op name, an error code, a
 * deadline instant), none of which is transaction data.
 *
 * @param kind       message kind, see {@link Kind}
 * @param sessionId  tunnel session the message concerns
 * @param corrId     request (frame) id the message concerns
 * @param origin     instance that originated the request (replies go to {@code tunnel:reply:{origin}})
 * @param op         op name of a REQUEST (lets the holder enforce {@code opsEffective})
 * @param deadlineAt epoch ms after which the holder releases the in-flight slot on its own
 * @param code       §7.11 code of a REJECT
 * @param frame      sealed outer frame text
 * @param pspId      PSP of a broadcast
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelayMessage(Kind kind, String sessionId, String corrId, String origin, String op,
                           Long deadlineAt, String code, String frame, Long pspId) {

    /** Message kinds. Holder-bound, origin-bound and broadcast. */
    public enum Kind {
        /** origin → holder: send this sealed request frame on the session and take an in-flight slot. */
        REQUEST,
        /** origin → holder: forward a sealed cancel frame. */
        CANCEL,
        /** origin → holder: forward a sealed credit frame. */
        CREDIT,
        /** origin → holder: the request reached its terminal frame; release the slot. */
        FINISH,
        /** origin → holder: a frame of this session failed to open (counts toward close 4401). */
        SEAL_FAILURE,
        /** any → holder: a newer session of the same node won; close this one with 4409. */
        CLOSE_REPLACED,
        /** any → holder: forward a sealed notify frame and route its hold.ack back to the holder. */
        NOTIFY,
        /** holder → origin: an Edge frame for the request, still sealed. */
        FRAME,
        /** holder → origin: the request could not be sent (code EDGE_BUSY / EDGE_OFFLINE / OP_NOT_ALLOWED). */
        REJECT,
        /** holder → origin: the session closed before the request finished. */
        SESSION_CLOSED,
        /** broadcast: support access was switched off for {@code pspId}; cancel platform pulls. */
        SUPPORT_REVOKED,
        /** broadcast: a new Edge config was issued for {@code pspId}; re-apply its tunnel section to held sessions. */
        CONFIG_CHANGED
    }

    /** Holder-bound REQUEST. */
    static RelayMessage request(String sessionId, String corrId, String origin, String op, long deadlineAt,
                                String frame) {
        return new RelayMessage(Kind.REQUEST, sessionId, corrId, origin, op, deadlineAt, null, frame, null);
    }

    /** Holder-bound message carrying a frame (CANCEL, CREDIT, NOTIFY). */
    static RelayMessage withFrame(Kind kind, String sessionId, String corrId, String origin, String frame) {
        return new RelayMessage(kind, sessionId, corrId, origin, null, null, null, frame, null);
    }

    /** Holder-bound message without a frame (FINISH, SEAL_FAILURE, CLOSE_REPLACED). */
    static RelayMessage control(Kind kind, String sessionId, String corrId) {
        return new RelayMessage(kind, sessionId, corrId, null, null, null, null, null, null);
    }

    /** Origin-bound FRAME. */
    static RelayMessage frame(String sessionId, String corrId, String frame) {
        return new RelayMessage(Kind.FRAME, sessionId, corrId, null, null, null, null, frame, null);
    }

    /** Origin-bound REJECT. */
    static RelayMessage reject(String sessionId, String corrId, TunnelErrorCode code) {
        return new RelayMessage(Kind.REJECT, sessionId, corrId, null, null, null, code.name(), null, null);
    }

    /** Origin-bound SESSION_CLOSED. */
    static RelayMessage sessionClosed(String sessionId, String corrId) {
        return new RelayMessage(Kind.SESSION_CLOSED, sessionId, corrId, null, null, null, null, null, null);
    }

    /** Broadcast SUPPORT_REVOKED. */
    static RelayMessage supportRevoked(long pspId) {
        return new RelayMessage(Kind.SUPPORT_REVOKED, null, null, null, null, null, null, null, pspId);
    }

    /** Broadcast CONFIG_CHANGED. */
    static RelayMessage configChanged(long pspId) {
        return new RelayMessage(Kind.CONFIG_CHANGED, null, null, null, null, null, null, null, pspId);
    }
}
