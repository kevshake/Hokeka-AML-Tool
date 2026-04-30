package com.posgateway.aml.aspect;

import com.posgateway.aml.service.PrometheusMetricsService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aspect to automatically collect metrics from service methods
 */
@Aspect
@Component
public class MetricsAspect {

    private final PrometheusMetricsService metricsService;

    @Autowired
    public MetricsAspect(PrometheusMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Around("execution(* com.posgateway.aml.service.TransactionIngestionService.*(..))")
    public Object measureTransactionProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordTransactionProcessingTime(duration);
            metricsService.incrementTransactionProcessed(null, "processed", null, null);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordTransactionProcessingTime(duration);
            throw e;
        }
    }

    @Around("execution(* com.posgateway.aml.service.AmlService.*(..))")
    public Object measureAmlAssessment(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordAmlAssessmentTime(duration, null, null);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordAmlAssessmentTime(duration, null, null);
            throw e;
        }
    }

    @Around("execution(* com.posgateway.aml.service.FraudDetectionService.*(..))")
    public Object measureFraudAssessment(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordFraudAssessmentTime(duration, null, null);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordFraudAssessmentTime(duration, null, null);
            throw e;
        }
    }

    @Around("execution(* com.posgateway.aml.service.*ScoringService.*(..))")
    public Object measureModelScoring(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordModelScoringTime(duration, null, null);
            metricsService.incrementModelScoring(true, null, null);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordModelScoringTime(duration, null, null);
            metricsService.incrementModelScoring(false, null, null);
            throw e;
        }
    }
}
