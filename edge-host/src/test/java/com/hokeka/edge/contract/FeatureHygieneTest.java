package com.hokeka.edge.contract;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract §2.4 feature-bag hygiene and the §2.5 Luhn guard. */
class FeatureHygieneTest {

    @Test
    void luhnGuardMatchesTheContractDefinition() {
        assertTrue(LuhnGuard.panLike("4111111111111111"));
        assertTrue(LuhnGuard.panLike("4111 1111 1111 1111"));
        assertTrue(LuhnGuard.panLike("4111-1111-1111-1111"));
        assertTrue(LuhnGuard.panLike("378282246310005"), "15-digit Amex test PAN");
        assertFalse(LuhnGuard.panLike("4111111111111112"), "fails the Luhn check");
        assertFalse(LuhnGuard.panLike("411111111111"), "12 digits is too short");
        assertFalse(LuhnGuard.panLike("41111111111111111111"), "20 digits is too long");
        assertFalse(LuhnGuard.panLike("AUTH-2026-10-16-000981"));
        assertFalse(LuhnGuard.panLike("4111x111111111111"));
        assertFalse(LuhnGuard.panLike((String) null));
        assertTrue(LuhnGuard.panLike(4111111111111111L));
        assertFalse(LuhnGuard.panLike(125000L));
        assertFalse(LuhnGuard.panLike(12.5));
    }

    @Test
    void dropsCardSecretsPanHashAndPanLikeValues() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("pan", "4111111111111111");
        in.put("pan_hash", "h");
        in.put("card_number", "x");
        in.put("cvv", "123");
        in.put("cvv2", "123");
        in.put("track1", "B4111");
        in.put("track2", ";4111=");
        in.put("pin_block", "ab");
        in.put("expiry", "12/29");
        in.put("memo", "card 4111 1111 1111 1111");
        in.put("note", "4111-1111-1111-1111");
        in.put("ref_number", 4111111111111111L);
        in.put("amount_cents", 125000);
        in.put("merchant_id", "mrc_00421");
        in.put("device_id", "dev-1");
        in.put("is_3ds", true);
        in.put("optional", null);

        Map<String, Object> out = FeatureHygiene.sanitize(in);

        for (String gone : List.of("pan", "pan_hash", "card_number", "cvv", "cvv2", "track1", "track2",
                "pin_block", "expiry", "note", "ref_number")) {
            assertFalse(out.containsKey(gone), gone + " must be stripped");
        }
        // "card 4111 1111 1111 1111" contains letters, so it is not PAN-like as a whole (§2.5 is a
        // whole-value rule); it stays, exactly as the contract's schemas would accept it.
        assertEquals("card 4111 1111 1111 1111", out.get("memo"));
        assertEquals(125000, out.get("amount_cents"));
        assertEquals("mrc_00421", out.get("merchant_id"));
        assertEquals(true, out.get("is_3ds"));
        assertTrue(out.containsKey("optional"));
    }

    @Test
    void dropsBadKeysNonScalarsAndLongStrings() {
        Map<String, Object> in = new HashMap<>();
        in.put("CamelCase", 1);
        in.put("with-dash", 1);
        in.put("1leading_digit", 1);
        in.put("nested", Map.of("a", 1));
        in.put("list", List.of(1, 2));
        in.put("long_text", "x".repeat(513));
        in.put("ok_text", "x".repeat(512));
        in.put("nan_value", Double.NaN);

        Map<String, Object> out = FeatureHygiene.sanitize(in);

        assertEquals(Map.of("ok_text", "x".repeat(512)), out);
    }

    @Test
    void capsTheBagAtTheTunnelSchemaMaximum() {
        Map<String, Object> in = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) {
            in.put("f_" + i, i);
        }
        assertEquals(FeatureHygiene.MAX_FEATURES, FeatureHygiene.sanitize(in).size());
        assertTrue(FeatureHygiene.sanitize(null).isEmpty());
    }
}
