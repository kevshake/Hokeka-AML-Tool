package com.posgateway.aml.service.residency;

/**
 * Published (inside the changing transaction) whenever an input of a PSP's Edge config document
 * changes: a data setting taking effect, or a CTR / tunnel / limit / blocklist edit. The Edge config
 * service listens after commit and mints the next configVersion.
 *
 * @param pspId  the PSP whose config inputs changed
 * @param reason short machine reason (e.g. {@code RETENTION_YEARS}, {@code SUPPORT_ACCESS}, {@code LIMITS})
 */
public record PspDataSettingsChangedEvent(Long pspId, String reason) {
}
