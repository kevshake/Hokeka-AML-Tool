package com.posgateway.aml.integration.cbk;

import java.util.Random;

/**
 * Wraps a JSON payload in the legacy multipart/mixed format required by the CBK gateway.
 *
 * <p>The legacy app (RestAssured-based) produced the following wire bytes verbatim
 * (reconstructed from {@code Cbk_Client.SendMessageToHost}):
 *
 * <pre>
 * ------=_Part_1_&lt;RANDOM_ID&gt;\n
 * Content-Type: multipart/mixed\n
 * Content-Transfer-Encoding: 8bit\n
 * \n
 * &lt;json-body&gt;\n
 * ------=_Part_1_&lt;RANDOM_ID&gt;--
 * </pre>
 *
 * <p>RANDOM_ID format: {@code &lt;9-digit&gt;.&lt;6-digit&gt;&lt;6-digit&gt;} (no dot between last two segments).
 * Example: {@code 123456789.123456789012}.
 *
 * <p>The {@code Content-Type} header sent on the HTTP request must be:
 * <pre>multipart/mixed; boundary="----=_Part_1_&lt;RANDOM_ID&gt;"</pre>
 *
 * <p>Use {@link #wrap(String)} to get both the boundary id and the full body string.
 */
public final class CbkMultipartWrapper {

    private CbkMultipartWrapper() {}

    /**
     * Wraps {@code jsonBody} in the CBK multipart format.
     *
     * @param jsonBody the JSON payload to wrap
     * @return a {@link Result} containing the boundary id and the complete multipart body string
     */
    public static Result wrap(String jsonBody) {
        String multipartId = generateMultipartId();
        String boundary = "----=_Part_1_" + multipartId;

        // Replicate the legacy body exactly: \n line endings as in the original
        String body = "--" + boundary + "\n"
                + "Content-Type: multipart/mixed\n"
                + "Content-Transfer-Encoding: 8bit\n"
                + "\n"
                + jsonBody + "\n"
                + "--" + boundary + "--";

        return new Result(multipartId, boundary, body);
    }

    /**
     * Generates a random multipart id matching the legacy format:
     * {@code <9-digit>.<6-digit><6-digit>} — three numeric segments.
     */
    private static String generateMultipartId() {
        Random r = new Random();
        int seg1 = r.nextInt(900_000_000) + 100_000_000; // 9 digits
        int seg2 = r.nextInt(900_000) + 100_000;          // 6 digits
        int seg3 = r.nextInt(900_000) + 100_000;          // 6 digits
        return seg1 + "." + seg2 + seg3;
    }

    /** Holds the generated boundary and the ready-to-send multipart body. */
    public static final class Result {
        private final String multipartId;
        private final String boundary;
        private final String body;

        Result(String multipartId, String boundary, String body) {
            this.multipartId = multipartId;
            this.boundary = boundary;
            this.body = body;
        }

        /** The random multipart id segment (without {@code ----=_Part_1_} prefix). */
        public String getMultipartId() { return multipartId; }

        /** The full boundary string, e.g. {@code ----=_Part_1_123456789.123456789012}. */
        public String getBoundary() { return boundary; }

        /**
         * The complete multipart body to send as the HTTP request body.
         */
        public String getBody() { return body; }

        /**
         * The {@code Content-Type} header value to use on the outgoing request,
         * e.g. {@code multipart/mixed; boundary="----=_Part_1_<id>"}.
         */
        public String getContentTypeHeaderValue() {
            return "multipart/mixed; boundary=\"" + boundary + "\"";
        }
    }
}
