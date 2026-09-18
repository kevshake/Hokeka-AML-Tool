package com.posgateway.aml.dto.onprem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin request to register a new on-prem instance. The plaintext client secret is returned
 * exactly once in the create response and is never stored.
 */
public record OnPremInstanceCreateRequest(
        @NotNull Long pspId,
        @NotBlank @Size(max = 128) String instanceId,
        @NotBlank @Size(max = 255) String displayName,
        @Min(1) @Max(365) Integer approvedDays
) {
}
