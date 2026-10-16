package com.posgateway.aml.entity.assessment;

/**
 * What caused this assessment to run (target model AssessmentContext trigger).
 */
public enum AssessmentTriggerType {
    TXN,
    TXN_EVENT,
    USER_EVENT,
    SCREENING_DELTA,
    DISPOSITION,
    BATCH
}
