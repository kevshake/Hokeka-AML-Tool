package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.psp.PspResponse;
import com.posgateway.aml.dto.psp.PspUserResponse;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PspMapper {

    @Mapping(target = "id", source = "pspId")
    PspResponse toResponse(Psp psp);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "role", source = "role.name")
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "status", expression = "java(user.isEnabled() ? \"ACTIVE\" : \"INACTIVE\")")
    PspUserResponse toResponse(User user);
}
