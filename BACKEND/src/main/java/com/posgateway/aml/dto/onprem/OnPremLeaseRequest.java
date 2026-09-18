package com.posgateway.aml.dto.onprem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Machine-to-machine lease request from an on-prem instance. */
public record OnPremLeaseRequest(
        @NotBlank @Size(max = 128) String clientId,
        @NotBlank @Size(max = 256) String clientSecret,
        @NotBlank @Size(max = 128) String instanceId,
        @Size(max = 255) String hostname,
        @Size(max = 64) String agentVersion
) {
}
