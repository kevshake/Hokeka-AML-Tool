package com.posgateway.aml.dto.psp;


public class InvoiceGenerationRequest {
    private Long pspId;
    private int month;
    private int year;

    public InvoiceGenerationRequest() {
    }

    public InvoiceGenerationRequest(Long pspId, int month, int year) {
        this.pspId = pspId;
        this.month = month;
        this.year = year;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    @Override
    public String toString() {
        return "InvoiceGenerationRequest{" +
                "pspId=" + pspId +
                ", month=" + month +
                ", year=" + year +
                '}';
    }
}
