package com.posgateway.aml.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Label Transaction Request DTO
 * Request DTO for labeling transactions
 */
public class LabelTransactionRequestDTO {

    @NotNull(message = "Transaction ID is required")
    private Long txnId;

    private Short label; // 1 = fraud, 0 = good, null = unknown

    @NotNull(message = "Investigator is required")
    private String investigator;

    private String notes;

    // Getters and Setters
    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public Short getLabel() {
        return label;
    }

    public void setLabel(Short label) {
        this.label = label;
    }

    public String getInvestigator() {
        return investigator;
    }

    public void setInvestigator(String investigator) {
        this.investigator = investigator;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

