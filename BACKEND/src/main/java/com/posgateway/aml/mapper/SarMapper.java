package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.compliance.SarResponse;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SarMapper {

    @Mapping(target = "reportId", source = "id")
    @Mapping(target = "caseId", source = "complianceCase.id")
    @Mapping(target = "filingDate", source = "filedAt")
    @Mapping(target = "createdBy", source = "createdBy.username")
    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "suspectInfo", ignore = true)
    @Mapping(target = "activityInfo", ignore = true)
    SarResponse toResponse(SuspiciousActivityReport sar);
}
