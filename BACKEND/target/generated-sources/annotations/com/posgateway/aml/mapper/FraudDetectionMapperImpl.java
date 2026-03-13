package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.FraudDetectionResponseDTO;
import com.posgateway.aml.service.AsyncFraudDetectionOrchestrator;
import com.posgateway.aml.service.FraudDetectionOrchestrator;
import com.posgateway.aml.service.HighConcurrencyFraudOrchestrator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-13T11:24:49+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class FraudDetectionMapperImpl implements FraudDetectionMapper {

    @Override
    public FraudDetectionResponseDTO toResponse(FraudDetectionOrchestrator.FraudDetectionResult result) {
        if ( result == null ) {
            return null;
        }

        FraudDetectionResponseDTO fraudDetectionResponseDTO = new FraudDetectionResponseDTO();

        Map<String, Object> map = result.getRiskDetails();
        if ( map != null ) {
            fraudDetectionResponseDTO.setRiskDetails( new LinkedHashMap<String, Object>( map ) );
        }
        fraudDetectionResponseDTO.setTxnId( result.getTxnId() );
        fraudDetectionResponseDTO.setAction( result.getAction() );
        List<String> list = result.getReasons();
        if ( list != null ) {
            fraudDetectionResponseDTO.setReasons( new ArrayList<String>( list ) );
        }
        fraudDetectionResponseDTO.setLatencyMs( result.getLatencyMs() );

        return fraudDetectionResponseDTO;
    }

    @Override
    public FraudDetectionResponseDTO toResponse(HighConcurrencyFraudOrchestrator.FraudDetectionResult result) {
        if ( result == null ) {
            return null;
        }

        FraudDetectionResponseDTO fraudDetectionResponseDTO = new FraudDetectionResponseDTO();

        fraudDetectionResponseDTO.setTxnId( result.getTxnId() );
        fraudDetectionResponseDTO.setAction( result.getAction() );
        List<String> list = result.getReasons();
        if ( list != null ) {
            fraudDetectionResponseDTO.setReasons( new ArrayList<String>( list ) );
        }
        fraudDetectionResponseDTO.setLatencyMs( result.getLatencyMs() );

        return fraudDetectionResponseDTO;
    }

    @Override
    public FraudDetectionResponseDTO toResponse(AsyncFraudDetectionOrchestrator.FraudDetectionResult result) {
        if ( result == null ) {
            return null;
        }

        FraudDetectionResponseDTO fraudDetectionResponseDTO = new FraudDetectionResponseDTO();

        fraudDetectionResponseDTO.setTxnId( result.getTxnId() );
        fraudDetectionResponseDTO.setAction( result.getAction() );
        List<String> list = result.getReasons();
        if ( list != null ) {
            fraudDetectionResponseDTO.setReasons( new ArrayList<String>( list ) );
        }
        fraudDetectionResponseDTO.setLatencyMs( result.getLatencyMs() );

        return fraudDetectionResponseDTO;
    }
}
