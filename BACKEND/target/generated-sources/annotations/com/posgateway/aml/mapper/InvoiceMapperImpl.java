package com.posgateway.aml.mapper;

import com.posgateway.aml.dto.psp.InvoiceResponse;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.InvoiceLineItem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-12T15:12:45+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class InvoiceMapperImpl implements InvoiceMapper {

    @Override
    public InvoiceResponse toResponse(Invoice invoice) {
        if ( invoice == null ) {
            return null;
        }

        InvoiceResponse.InvoiceResponseBuilder invoiceResponse = InvoiceResponse.builder();

        invoiceResponse.amount( invoice.getTotalAmount() );
        invoiceResponse.periodStart( invoice.getBillingPeriodStart() );
        invoiceResponse.periodEnd( invoice.getBillingPeriodEnd() );
        invoiceResponse.invoiceNumber( invoice.getInvoiceNumber() );
        invoiceResponse.status( invoice.getStatus() );
        invoiceResponse.lineItems( invoiceLineItemListToLineItemList( invoice.getLineItems() ) );

        return invoiceResponse.build();
    }

    @Override
    public InvoiceResponse.LineItem toLineItemResponse(InvoiceLineItem item) {
        if ( item == null ) {
            return null;
        }

        InvoiceResponse.LineItem.LineItemBuilder lineItem = InvoiceResponse.LineItem.builder();

        lineItem.total( item.getLineTotal() );
        lineItem.serviceType( item.getServiceType() );
        lineItem.description( item.getDescription() );
        if ( item.getQuantity() != null ) {
            lineItem.quantity( item.getQuantity() );
        }

        return lineItem.build();
    }

    protected List<InvoiceResponse.LineItem> invoiceLineItemListToLineItemList(List<InvoiceLineItem> list) {
        if ( list == null ) {
            return null;
        }

        List<InvoiceResponse.LineItem> list1 = new ArrayList<InvoiceResponse.LineItem>( list.size() );
        for ( InvoiceLineItem invoiceLineItem : list ) {
            list1.add( toLineItemResponse( invoiceLineItem ) );
        }

        return list1;
    }
}
