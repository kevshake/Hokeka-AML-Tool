package com.posgateway.aml.dto.onprem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OnPremApprovedDaysRequest(
        @NotNull @Min(1) @Max(365) Integer approvedDays
) {
}
