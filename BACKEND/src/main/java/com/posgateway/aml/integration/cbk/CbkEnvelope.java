package com.posgateway.aml.integration.cbk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the outer JSON envelope required by every CBK GDI report endpoint.
 *
 * <p>Wire format:
 * <pre>
 * {
 *   "INSTITUTION_CODE": "&lt;instCode&gt;",
 *   "REQUEST_ID": "",
 *   "REPORTING_DATE": "yyyy-MM-dd",
 *   "IS_ATTACHED": "N",
 *   "&lt;WRAPPER_KEY&gt;": [ { record fields … } ]
 * }
 * </pre>
 *
 * <p>Two date conventions are used by the CBK API:
 * <ul>
 *   <li>{@link #forToday} — uses today's date (annual reference snapshots: endpoints #1–4).</li>
 *   <li>{@link #forYesterday} — uses yesterday's date (operational / transactional endpoints).</li>
 * </ul>
 */
public final class CbkEnvelope {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CbkEnvelope() {}

    /**
     * Builds the JSON envelope with {@code REPORTING_DATE} = today.
     *
     * <p>Used for annual reference endpoints (#1 Senior Management, #2 Directors,
     * #3 Trustees, #4 Shareholders).
     *
     * @param mapper      Jackson ObjectMapper (Spring-injected)
     * @param instCode    PSP institution code
     * @param wrapperKey  the payload wrapper key (e.g. {@code "SENIOR_MNGT_SCHEDULE"})
     * @param records     list of record objects (must be Jackson-serialisable)
     * @return the complete JSON string
     */
    public static String forToday(ObjectMapper mapper, String instCode, String wrapperKey, List<?> records) {
        return build(mapper, instCode, wrapperKey, records, LocalDate.now());
    }

    /**
     * Builds the JSON envelope with {@code REPORTING_DATE} = yesterday.
     *
     * <p>Used for all operational/transactional endpoints (#5–#17).
     *
     * @param mapper      Jackson ObjectMapper (Spring-injected)
     * @param instCode    PSP institution code
     * @param wrapperKey  the payload wrapper key (e.g. {@code "INCIDENTS_DATA"})
     * @param records     list of record objects (must be Jackson-serialisable)
     * @return the complete JSON string
     */
    public static String forYesterday(ObjectMapper mapper, String instCode, String wrapperKey, List<?> records) {
        return build(mapper, instCode, wrapperKey, records, LocalDate.now().minusDays(1));
    }

    // ---- private ----

    private static String build(ObjectMapper mapper, String instCode, String wrapperKey,
                                List<?> records, LocalDate reportingDate) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("INSTITUTION_CODE", instCode);
            root.put("REQUEST_ID", "");
            root.put("REPORTING_DATE", reportingDate.format(DATE_FMT));
            root.put("IS_ATTACHED", "N");

            ArrayNode arr = mapper.createArrayNode();
            for (Object rec : records) {
                arr.add(mapper.valueToTree(rec));
            }
            root.set(wrapperKey, arr);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new CbkTokenService.CbkGdiException("Failed to build CBK envelope: " + e.getMessage(), e);
        }
    }
}
