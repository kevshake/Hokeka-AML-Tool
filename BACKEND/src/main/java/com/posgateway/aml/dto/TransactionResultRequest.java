package com.posgateway.aml.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record TransactionResultRequest(
        @NotNull @JsonAlias({"txnId"}) Long transactionId,
        String merchantId,
        @Pattern(regexp = "^[0-9A-Za-z]{2,3}$") String responseCode,
        Boolean isChargeback,
        @PositiveOrZero Long amountCents,
        String billClassificationCode) {
}
