package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.psp.PspResponse;
import com.posgateway.aml.dto.psp.PspUserResponse;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-04T20:42:34+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.12.1 (Microsoft)"
)
@Component
public class PspMapperImpl implements PspMapper {

    @Override
    public PspResponse toResponse(Psp psp) {
        if ( psp == null ) {
            return null;
        }

        PspResponse pspResponse = new PspResponse();

        pspResponse.setId( psp.getPspId() );
        pspResponse.setIsTestMode( psp.getIsTestMode() );
        pspResponse.setPspCode( psp.getPspCode() );
        pspResponse.setLegalName( psp.getLegalName() );
        pspResponse.setTradingName( psp.getTradingName() );
        pspResponse.setCountry( psp.getCountry() );
        pspResponse.setContactEmail( psp.getContactEmail() );
        pspResponse.setContactPhone( psp.getContactPhone() );
        pspResponse.setContactAddress( psp.getContactAddress() );
        pspResponse.setRegistrationNumber( psp.getRegistrationNumber() );
        pspResponse.setTaxId( psp.getTaxId() );
        pspResponse.setStatus( psp.getStatus() );
        pspResponse.setKycEnabled( psp.getKycEnabled() );
        pspResponse.setBillingPlan( psp.getBillingPlan() );
        pspResponse.setBillingCycle( psp.getBillingCycle() );
        pspResponse.setCurrency( psp.getCurrency() );
        pspResponse.setPaymentTerms( psp.getPaymentTerms() );
        pspResponse.setCbkReportingEnabled( psp.getCbkReportingEnabled() );
        pspResponse.setCbkEnvironment( psp.getCbkEnvironment() );
        pspResponse.setLogoUrl( psp.getLogoUrl() );
        pspResponse.setPrimaryColor( psp.getPrimaryColor() );
        pspResponse.setSecondaryColor( psp.getSecondaryColor() );
        pspResponse.setAccentColor( psp.getAccentColor() );
        pspResponse.setFontFamily( psp.getFontFamily() );
        pspResponse.setBrandingTheme( psp.getBrandingTheme() );
        pspResponse.setCreatedAt( psp.getCreatedAt() );
        pspResponse.setUpdatedAt( psp.getUpdatedAt() );
        pspResponse.setOnboardedAt( psp.getOnboardedAt() );

        return pspResponse;
    }

    @Override
    public PspUserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        PspUserResponse.PspUserResponseBuilder pspUserResponse = PspUserResponse.builder();

        pspUserResponse.id( user.getId() );
        pspUserResponse.role( userRoleName( user ) );
        pspUserResponse.email( user.getEmail() );

        pspUserResponse.fullName( user.getFullName() );
        pspUserResponse.status( user.isEnabled() ? "ACTIVE" : "INACTIVE" );

        return pspUserResponse.build();
    }

    private String userRoleName(User user) {
        if ( user == null ) {
            return null;
        }
        Role role = user.getRole();
        if ( role == null ) {
            return null;
        }
        String name = role.getName();
        if ( name == null ) {
            return null;
        }
        return name;
    }
}
