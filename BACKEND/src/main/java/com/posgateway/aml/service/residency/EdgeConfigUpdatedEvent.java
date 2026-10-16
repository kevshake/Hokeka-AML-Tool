package com.posgateway.aml.service.residency;

/**
 * Published after a new Edge config version is issued for a PSP (contract 6.2: "Any change to the
 * inputs MUST increment configVersion and SHOULD trigger notify config.updated to every connected
 * node of the PSP"). The Edge Tunnel server subscribes to send {@code notify config.updated}.
 *
 * @param pspId         the PSP whose config changed
 * @param configVersion the newly issued version
 */
public record EdgeConfigUpdatedEvent(Long pspId, long configVersion) {
}
