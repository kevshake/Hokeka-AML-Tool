package com.posgateway.aml.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class PiiMaskingAspect {

    private static final Logger logger = LoggerFactory.getLogger(PiiMaskingAspect.class);

    @Around("execution(* com.posgateway.aml.service..*(..))")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // Simple masking logic for arguments (e.g. if arg is a string resembling a card
        // number)
        // In a real app, use annotations @LogMask on parameters
        Object[] maskedArgs = Arrays.stream(args)
                .map(this::maskData)
                .toArray();

        logger.debug("Entering: {} with arguments: {}", methodName, Arrays.toString(maskedArgs));

        try {
            Object result = joinPoint.proceed();
            logger.debug("Exiting: {} with result: {}", methodName, maskData(result));
            return result;
        } catch (Throwable t) {
            logger.error("Exception in: {} with cause: {}", methodName, t.getMessage());
            throw t;
        }
    }

    private Object maskData(Object obj) {
        if (obj instanceof String) {
            String str = (String) obj;
            // Mask Email
            if (str.contains("@")) {
                return str.replaceAll("(^[^@]{3})[^@]*(@.*$)", "$1***$2");
            }
            // Mask Card (simple regex for 16 digits)
            if (str.matches(".*\\d{16}.*")) {
                return str.replaceAll("\\d{12}(\\d{4})", "**** **** **** $1");
            }
        }
        return obj;
    }
}
