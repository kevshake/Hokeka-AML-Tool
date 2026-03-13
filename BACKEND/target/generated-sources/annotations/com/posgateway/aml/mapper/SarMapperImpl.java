package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.compliance.SarResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-13T11:24:50+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class SarMapperImpl implements SarMapper {

    @Override
    public SarResponse toResponse(SuspiciousActivityReport sar) {
        if ( sar == null ) {
            return null;
        }

        SarResponse.SarResponseBuilder sarResponse = SarResponse.builder();

        sarResponse.reportId( sar.getId() );
        sarResponse.caseId( sarComplianceCaseId( sar ) );
        sarResponse.filingDate( sar.getFiledAt() );
        sarResponse.createdBy( sarCreatedByUsername( sar ) );
        if ( sar.getStatus() != null ) {
            sarResponse.status( sar.getStatus().name() );
        }
        sarResponse.narrative( sar.getNarrative() );
        sarResponse.createdAt( sar.getCreatedAt() );

        return sarResponse.build();
    }

    private Long sarComplianceCaseId(SuspiciousActivityReport suspiciousActivityReport) {
        if ( suspiciousActivityReport == null ) {
            return null;
        }
        ComplianceCase complianceCase = suspiciousActivityReport.getComplianceCase();
        if ( complianceCase == null ) {
            return null;
        }
        Long id = complianceCase.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private String sarCreatedByUsername(SuspiciousActivityReport suspiciousActivityReport) {
        if ( suspiciousActivityReport == null ) {
            return null;
        }
        User createdBy = suspiciousActivityReport.getCreatedBy();
        if ( createdBy == null ) {
            return null;
        }
        String username = createdBy.getUsername();
        if ( username == null ) {
            return null;
        }
        return username;
    }
}
