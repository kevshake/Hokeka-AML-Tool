package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/**
 * RFC 8785 JSON Canonicalization Scheme (JCS), as required for the cursor {@code q} hash (§7.5)
 * and the audit {@code params_digest} (§7.15): SHA-256 hex of the canonical JSON of the op params.
 *
 * <p>Members are sorted by UTF-16 code units ({@link String#compareTo} is exactly that), strings
 * are escaped as ECMAScript {@code JSON.stringify} does, and numbers are rendered with the
 * ECMAScript {@code Number.prototype.toString} algorithm (shortest round-trip digits, which
 * {@link Double#toString} produces on JDK 19+).
 */
public final class Jcs {

    private static final BigInteger ES_INTEGER_LIMIT = BigInteger.TEN.pow(21);

    /** Utility class. */
    private Jcs() {
    }

    /** Canonical JSON text of {@code node}. */
    public static String canonicalize(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    /** SHA-256 hex (lowercase) over the UTF-8 bytes of the canonical JSON of {@code node}. */
    public static String sha256Hex(JsonNode node) {
        return sha256Hex(canonicalize(node).getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 hex (lowercase) of raw bytes. */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Recursive writer dispatching on the JSON node type. */
    private static void write(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            sb.append("null");
        } else if (node.isObject()) {
            List<String> names = new ArrayList<>();
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                names.add(it.next());
            }
            Collections.sort(names);
            sb.append('{');
            boolean first = true;
            for (String name : names) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(name, sb);
                sb.append(':');
                write(node.get(name), sb);
            }
            sb.append('}');
        } else if (node.isArray()) {
            sb.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(node.get(i), sb);
            }
            sb.append(']');
        } else if (node.isTextual()) {
            writeString(node.asText(), sb);
        } else if (node.isBoolean()) {
            sb.append(node.booleanValue() ? "true" : "false");
        } else if (node.isNumber()) {
            sb.append(number(node));
        } else {
            throw new IllegalArgumentException("JCS: unsupported JSON node type " + node.getNodeType());
        }
    }

    /** ECMAScript JSON.stringify string escaping. */
    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    boolean loneSurrogate = Character.isSurrogate(c) && !(
                            (Character.isHighSurrogate(c) && i + 1 < s.length()
                                    && Character.isLowSurrogate(s.charAt(i + 1)))
                            || (Character.isLowSurrogate(c) && i > 0
                                    && Character.isHighSurrogate(s.charAt(i - 1))));
                    if (c < 0x20 || loneSurrogate) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** ECMAScript Number::toString for a JSON number node. */
    static String number(JsonNode node) {
        if (node.isIntegralNumber()) {
            BigInteger v = node.bigIntegerValue();
            if (v.abs().compareTo(ES_INTEGER_LIMIT) < 0) {
                return v.toString();
            }
            return esDouble(v.doubleValue());
        }
        if (node.isBigDecimal()) {
            BigDecimal d = node.decimalValue();
            return esDouble(d.doubleValue());
        }
        return esDouble(node.doubleValue());
    }

    /** ECMAScript Number.prototype.toString(10) for a finite double. */
    static String esDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("JCS: NaN and Infinity are not valid JSON numbers");
        }
        if (d == 0) {
            return "0";
        }
        String sign = d < 0 ? "-" : "";
        String java = Double.toString(Math.abs(d)); // shortest round-trip digits (JDK 19+)
        String mantissa;
        int exp10;
        int ePos = java.indexOf('E');
        if (ePos >= 0) {
            mantissa = java.substring(0, ePos);
            exp10 = Integer.parseInt(java.substring(ePos + 1));
        } else {
            mantissa = java;
            exp10 = 0;
        }
        int dot = mantissa.indexOf('.');
        String intPart = dot >= 0 ? mantissa.substring(0, dot) : mantissa;
        String fracPart = dot >= 0 ? mantissa.substring(dot + 1) : "";
        String allDigits = intPart + fracPart;
        // value = 0.allDigits × 10^(intPart.length + exp10); then strip leading/trailing zeros.
        int n = intPart.length() + exp10;
        int lead = 0;
        while (lead < allDigits.length() - 1 && allDigits.charAt(lead) == '0') {
            lead++;
            n--;
        }
        String s = allDigits.substring(lead);
        int end = s.length();
        while (end > 1 && s.charAt(end - 1) == '0') {
            end--;
        }
        s = s.substring(0, end);
        int k = s.length();
        StringBuilder out = new StringBuilder(sign);
        if (k <= n && n <= 21) {
            out.append(s);
            out.append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            out.append(s, 0, n).append('.').append(s.substring(n));
        } else if (-6 < n && n <= 0) {
            out.append("0.").append("0".repeat(-n)).append(s);
        } else {
            int e = n - 1;
            out.append(s.charAt(0));
            if (k > 1) {
                out.append('.').append(s.substring(1));
            }
            out.append('e').append(e >= 0 ? '+' : '-').append(Math.abs(e));
        }
        return out.toString();
    }
}
