package com.posgateway.aml.service.edgedata;

import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelError;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The {@code {id}} of a tunnel-backed transaction path: a decision record id (UUID) or, for links
 * that predate R8, a legacy cloud transaction id (digits). Anything else is
 * {@code 400 INVALID_PARAMS} naming the pointer only, so the raw value never reaches an exception
 * message or a log.
 *
 * @param edgeAssessmentId the UUID form, or {@code null}
 * @param legacyId         the numeric form, or {@code null}
 */
public record TransactionId(UUID edgeAssessmentId, Long legacyId) {

    private static final Pattern UUID_SHAPE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,18}$");

    /** Parses a path id; {@code pointer} names it in the error. */
    public static TransactionId parse(String raw, String pointer) {
        if (raw != null && UUID_SHAPE.matcher(raw).matches()) {
            return new TransactionId(UUID.fromString(raw.toLowerCase(Locale.ROOT)), null);
        }
        if (raw != null && DIGITS.matcher(raw).matches()) {
            return new TransactionId(null, Long.parseLong(raw));
        }
        throw new EdgeDataException(TunnelError.invalidParams(pointer));
    }

    /** Parses an id that must be a decision record id (batchGet, related). */
    public static UUID parseUuid(String raw, String pointer) {
        if (raw != null && UUID_SHAPE.matcher(raw).matches()) {
            return UUID.fromString(raw.toLowerCase(Locale.ROOT));
        }
        throw new EdgeDataException(TunnelError.invalidParams(pointer));
    }

    /** True for the UUID form. */
    public boolean isEdge() {
        return edgeAssessmentId != null;
    }
}
