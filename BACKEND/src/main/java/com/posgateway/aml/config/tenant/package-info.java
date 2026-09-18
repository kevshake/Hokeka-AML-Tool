/**
 * Hibernate tenant filter definition shared by PSP-scoped entities.
 *
 * <p>Condition {@code (:pspId = 0 OR psp_id = :pspId)} mirrors {@code PspIsolationService}:
 * platform administrators bind {@code pspId = 0} and bypass the filter; PSP users bind their
 * own id so cross-tenant rows are invisible even when a repository omits an explicit
 * {@code WHERE psp_id = ?} clause.
 */
@org.hibernate.annotations.FilterDef(
        name = PspTenantFilter.NAME,
        parameters = @org.hibernate.annotations.ParamDef(name = PspTenantFilter.PARAM, type = Long.class),
        defaultCondition = "(:pspId = 0 OR psp_id = :pspId)")
package com.posgateway.aml.config.tenant;
