package com.posgateway.aml.edge.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

/**
 * Offline validator for control-plane-issued access tokens.
 *
 * <p>Realises the "authentication is central, enforcement is local" model: Hokeka issues a signed
 * token to a PSP's API node; the edge verifies it <b>entirely offline</b> using the control plane's
 * pinned Ed25519 public key (no per-transaction call to Hokeka), so the decision path stays local
 * and survives central downtime. Token format (compact, JWS-like):
 *
 * <pre>base64url(claimsJson) "." base64url(ed25519Signature)</pre>
 *
 * where the signature is over the ASCII bytes of the {@code base64url(claimsJson)} segment.
 */
public final class EdgeTokenValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicKey controlPlaneEd25519Pub;
    private final String expectedIssuer;
    private final String expectedAudience; // this edge's id
    private final long clockSkewSeconds;

    public EdgeTokenValidator(PublicKey controlPlaneEd25519Pub, String expectedIssuer,
                              String expectedAudience) {
        this(controlPlaneEd25519Pub, expectedIssuer, expectedAudience, 60);
    }

    public EdgeTokenValidator(PublicKey controlPlaneEd25519Pub, String expectedIssuer,
                              String expectedAudience, long clockSkewSeconds) {
        this.controlPlaneEd25519Pub = controlPlaneEd25519Pub;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public record Claims(String issuer, String subject, String audience, long expEpochSec, long iatEpochSec) {}

    /** Validate a token at time {@code now}; throws {@link SecurityException} if not acceptable. */
    public Claims validate(String token, Instant now) {
        try {
            int dot = token == null ? -1 : token.indexOf('.');
            if (dot <= 0 || dot == token.length() - 1) {
                throw new SecurityException("malformed token");
            }
            String claimsSeg = token.substring(0, dot);
            byte[] signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));

            // 1) Signature over the claims segment, verified with the pinned control-plane key.
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(controlPlaneEd25519Pub);
            s.update(claimsSeg.getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(signature)) {
                throw new SecurityException("token signature invalid");
            }

            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(claimsSeg));
            String iss = claims.path("iss").asText();
            String aud = claims.path("aud").asText();
            long exp = claims.path("exp").asLong();
            long iat = claims.path("iat").asLong();

            // 2) Standard claim checks (offline).
            long nowSec = now.getEpochSecond();
            if (expectedIssuer != null && !expectedIssuer.equals(iss)) {
                throw new SecurityException("unexpected token issuer");
            }
            if (expectedAudience != null && !expectedAudience.equals(aud)) {
                throw new SecurityException("token audience mismatch");
            }
            if (exp > 0 && nowSec > exp + clockSkewSeconds) {
                throw new SecurityException("token expired");
            }
            if (iat > 0 && iat > nowSec + clockSkewSeconds) {
                throw new SecurityException("token not yet valid");
            }
            return new Claims(iss, claims.path("sub").asText(), aud, exp, iat);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("token validation failed", e);
        }
    }
}
