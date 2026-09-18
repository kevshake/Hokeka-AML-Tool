package com.posgateway.aml.client.regulator;

/**
 * Upstream regulator transport failure with the response evidence preserved.
 */
public class RegulatorTransportException extends RuntimeException {

    private final Integer httpStatus;
    private final String regulatorStatus;
    private final String responseBody;

    public RegulatorTransportException(
            String message,
            Integer httpStatus,
            String regulatorStatus,
            String responseBody) {
        super(message);
        this.httpStatus = httpStatus;
        this.regulatorStatus = regulatorStatus;
        this.responseBody = responseBody;
    }

    public RegulatorTransportException(
            String message,
            Integer httpStatus,
            String regulatorStatus,
            String responseBody,
            Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.regulatorStatus = regulatorStatus;
        this.responseBody = responseBody;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getRegulatorStatus() {
        return regulatorStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
