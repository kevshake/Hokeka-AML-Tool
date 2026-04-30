package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.FraudDetectionResponseDTO;
import com.posgateway.aml.service.HighConcurrencyFraudOrchestrator;
import com.posgateway.aml.service.AsyncFraudDetectionOrchestrator;
import com.posgateway.aml.service.FraudDetectionOrchestrator.FraudDetectionResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FraudDetectionMapper {

    @org.mapstruct.Mapping(target = "riskDetails", source = "riskDetails")
    FraudDetectionResponseDTO toResponse(FraudDetectionResult result);

    @org.mapstruct.Mapping(target = "riskDetails", ignore = true)
    FraudDetectionResponseDTO toResponse(HighConcurrencyFraudOrchestrator.FraudDetectionResult result);

    @org.mapstruct.Mapping(target = "riskDetails", ignore = true)
    FraudDetectionResponseDTO toResponse(AsyncFraudDetectionOrchestrator.FraudDetectionResult result);
}
