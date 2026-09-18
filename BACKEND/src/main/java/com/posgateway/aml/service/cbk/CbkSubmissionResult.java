package com.posgateway.aml.service.cbk;

/**
 * Outcome of a single CBK GDI endpoint submission attempt.
 *
 * <p>Returned by {@link CbkSubmissionOrchestrator#runSingleEndpoint} and used by
 * the manual-retry controller endpoint.
 */
public final class CbkSubmissionResult {

    public enum Outcome {
        /** Remote CBK API returned a 2xx response. */
        SUCCESS,
        /** Remote CBK API returned a non-2xx response, or PSP is not configured. */
        FAILURE,
        /** PSP does not have CBK reporting enabled or is missing institution code. */
        SKIPPED
    }

    private final Long pspId;
    private final CbkEndpointType endpointType;
    private final Outcome outcome;
    private final int httpStatus;
    private final String referenceNumber;
    private final String errorMessage;
    /** Database id of the persisted {@code CbkSubmission} row, if one was created. */
    private final Long submissionId;

    private CbkSubmissionResult(Builder builder) {
        this.pspId = builder.pspId;
        this.endpointType = builder.endpointType;
        this.outcome = builder.outcome;
        this.httpStatus = builder.httpStatus;
        this.referenceNumber = builder.referenceNumber;
        this.errorMessage = builder.errorMessage;
        this.submissionId = builder.submissionId;
    }

    public Long getPspId() { return pspId; }
    public CbkEndpointType getEndpointType() { return endpointType; }
    public Outcome getOutcome() { return outcome; }
    public int getHttpStatus() { return httpStatus; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getErrorMessage() { return errorMessage; }
    public Long getSubmissionId() { return submissionId; }

    public boolean isSuccess() { return Outcome.SUCCESS == outcome; }

    @Override
    public String toString() {
        return "CbkSubmissionResult{pspId=" + pspId
                + ", endpoint=" + endpointType
                + ", outcome=" + outcome
                + ", httpStatus=" + httpStatus
                + ", ref=" + referenceNumber + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long pspId;
        private CbkEndpointType endpointType;
        private Outcome outcome;
        private int httpStatus;
        private String referenceNumber;
        private String errorMessage;
        private Long submissionId;

        private Builder() {}

        public Builder pspId(Long pspId) { this.pspId = pspId; return this; }
        public Builder endpointType(CbkEndpointType type) { this.endpointType = type; return this; }
        public Builder outcome(Outcome outcome) { this.outcome = outcome; return this; }
        public Builder httpStatus(int code) { this.httpStatus = code; return this; }
        public Builder referenceNumber(String ref) { this.referenceNumber = ref; return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }
        public Builder submissionId(Long id) { this.submissionId = id; return this; }

        public CbkSubmissionResult build() {
            return new CbkSubmissionResult(this);
        }
    }
}
