package com.posgateway.aml.service.billing;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.InvoiceLineItem;
import com.posgateway.aml.entity.psp.Psp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Generates professional invoice PDFs using OpenPDF (com.lowagie.text.*).
 * All bytes are produced in memory — no temp files.
 */
@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

    // Brand palette
    private static final Color BRAND_BURGUNDY = new Color(0x8B, 0x40, 0x49);
    private static final Color BODY_DARK      = new Color(0x33, 0x33, 0x33);
    private static final Color LIGHT_GRAY     = new Color(0xF5, 0xF5, 0xF5);
    private static final Color MID_GRAY       = new Color(0xCC, 0xCC, 0xCC);
    private static final Color WHITE          = Color.WHITE;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Value("${billing.invoice.company-name:Hokeka AML Platform}")
    private String companyName;

    @Value("${billing.invoice.company-address:Nairobi, Kenya}")
    private String companyAddress;

    @Value("${billing.invoice.support-email:billing@hokeka.com}")
    private String supportEmail;

    /**
     * Generate a professional PDF for the given invoice.
     *
     * @param invoice fully-populated Invoice (including lineItems and psp)
     * @return raw PDF bytes
     */
    public byte[] generateInvoicePdf(Invoice invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 60, 60);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, invoice);
            document.add(spacer(16));
            addBillingInfoRow(document, invoice);
            document.add(spacer(16));
            addMetadataTable(document, invoice);
            document.add(spacer(20));
            addLineItemsTable(document, invoice);
            document.add(spacer(16));
            addTotalsSection(document, invoice);
            document.add(spacer(20));
            addPaymentInfo(document, invoice);
            document.add(spacer(30));
            addFooter(document);

        } catch (Exception e) {
            log.error("Failed to generate PDF for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed for invoice " + invoice.getInvoiceNumber(), e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    private void addHeader(Document doc, Invoice invoice) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, BRAND_BURGUNDY);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, BODY_DARK);

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});

        // Left: company name block
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);
        Paragraph companyPara = new Paragraph(companyName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_BURGUNDY));
        companyPara.add(Chunk.NEWLINE);
        companyPara.add(new Chunk(companyAddress, FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK)));
        companyPara.add(Chunk.NEWLINE);
        companyPara.add(new Chunk(supportEmail, FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK)));
        leftCell.addElement(companyPara);
        headerTable.addCell(leftCell);

        // Right: INVOICE title + number
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPadding(0);
        Paragraph invTitle = new Paragraph("INVOICE", titleFont);
        invTitle.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(invTitle);
        Paragraph invNum = new Paragraph(invoice.getInvoiceNumber(), subtitleFont);
        invNum.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(invNum);
        headerTable.addCell(rightCell);

        doc.add(headerTable);

        // Divider line
        LineSeparator separator = new LineSeparator(2f, 100f, BRAND_BURGUNDY, Element.ALIGN_CENTER, -5f);
        doc.add(new Chunk(separator));
    }

    private void addBillingInfoRow(Document doc, Invoice invoice) throws DocumentException {
        Psp psp = invoice.getPsp();

        PdfPTable billingTable = new PdfPTable(2);
        billingTable.setWidthPercentage(100);
        billingTable.setWidths(new float[]{50, 50});

        // Bill From
        PdfPCell fromCell = buildInfoCell("Bill From", new String[]{
                companyName,
                companyAddress,
                supportEmail
        });
        billingTable.addCell(fromCell);

        // Bill To
        String pspDisplayName = psp.getTradingName() != null && !psp.getTradingName().isBlank()
                ? psp.getTradingName() : psp.getLegalName();
        String address = psp.getContactAddress() != null ? psp.getContactAddress() : psp.getCountry();
        PdfPCell toCell = buildInfoCell("Bill To", new String[]{
                pspDisplayName,
                psp.getLegalName(),
                address,
                psp.getContactEmail()
        });
        billingTable.addCell(toCell);

        doc.add(billingTable);
    }

    private PdfPCell buildInfoCell(String heading, String[] lines) {
        Font headFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, WHITE);
        Font lineFont  = FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(8);

        // Heading badge
        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(100);
        PdfPCell badgeCell = new PdfPCell(new Phrase(heading, headFont));
        badgeCell.setBackgroundColor(BRAND_BURGUNDY);
        badgeCell.setPadding(4);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badge.addCell(badgeCell);
        cell.addElement(badge);
        cell.addElement(spacer(4));

        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                cell.addElement(new Paragraph(line, lineFont));
            }
        }
        return cell;
    }

    private void addMetadataTable(Document doc, Invoice invoice) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BODY_DARK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK);

        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{25, 25, 25, 25});

        String[][] rows = {
                {"Invoice #",     invoice.getInvoiceNumber()},
                {"Issue Date",    invoice.getGeneratedAt() != null
                                    ? invoice.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "—"},
                {"Due Date",      invoice.getDueDate() != null ? invoice.getDueDate().format(DATE_FMT) : "—"},
                {"Status",        invoice.getStatus()},
                {"Period Start",  invoice.getBillingPeriodStart() != null ? invoice.getBillingPeriodStart().format(DATE_FMT) : "—"},
                {"Period End",    invoice.getBillingPeriodEnd() != null ? invoice.getBillingPeriodEnd().format(DATE_FMT) : "—"},
                {"Currency",      invoice.getCurrency()},
                {"PSP Code",      invoice.getPsp().getPspCode()}
        };

        for (String[] row : rows) {
            PdfPCell labelCell = new PdfPCell(new Phrase(row[0], labelFont));
            labelCell.setBackgroundColor(LIGHT_GRAY);
            labelCell.setPadding(6);
            labelCell.setBorder(Rectangle.BOX);
            labelCell.setBorderColor(MID_GRAY);
            meta.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(row[1], valueFont));
            valueCell.setPadding(6);
            valueCell.setBorder(Rectangle.BOX);
            valueCell.setBorderColor(MID_GRAY);
            meta.addCell(valueCell);
        }

        doc.add(meta);
    }

    private void addLineItemsTable(Document doc, Invoice invoice) throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, WHITE);
        Font bodyFont   = FontFactory.getFont(FontFactory.HELVETICA, 8, BODY_DARK);

        Paragraph sectionTitle = new Paragraph("Services", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_BURGUNDY));
        doc.add(sectionTitle);
        doc.add(spacer(6));

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30, 20, 10, 20, 20});

        String[] headers = {"Description", "Service Type", "Qty", "Unit Price", "Total"};
        for (String h : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(h, headerFont));
            hCell.setBackgroundColor(BRAND_BURGUNDY);
            hCell.setPadding(7);
            hCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(hCell);
        }

        boolean alt = false;
        for (InvoiceLineItem item : invoice.getLineItems()) {
            Color rowBg = alt ? LIGHT_GRAY : WHITE;
            alt = !alt;

            addBodyCell(table, item.getDescription(), bodyFont, rowBg, Element.ALIGN_LEFT);
            addBodyCell(table, item.getServiceType(), bodyFont, rowBg, Element.ALIGN_LEFT);
            addBodyCell(table, String.valueOf(item.getQuantity()), bodyFont, rowBg, Element.ALIGN_RIGHT);
            addBodyCell(table, formatAmount(item.getUnitPrice(), invoice.getCurrency()), bodyFont, rowBg, Element.ALIGN_RIGHT);
            addBodyCell(table, formatAmount(item.getLineTotal(), invoice.getCurrency()), bodyFont, rowBg, Element.ALIGN_RIGHT);
        }

        if (invoice.getLineItems().isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No line items", bodyFont));
            emptyCell.setColspan(5);
            emptyCell.setPadding(8);
            emptyCell.setBorder(Rectangle.BOX);
            emptyCell.setBorderColor(MID_GRAY);
            table.addCell(emptyCell);
        }

        doc.add(table);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(MID_GRAY);
        table.addCell(cell);
    }

    private void addTotalsSection(Document doc, Invoice invoice) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK);
        Font boldFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BODY_DARK);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_BURGUNDY);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(50);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new float[]{60, 40});

        addTotalRow(totals, "Subtotal", formatAmount(invoice.getSubtotal(), invoice.getCurrency()), labelFont, labelFont);

        BigDecimal tax = invoice.getTaxAmount();
        if (tax != null && tax.compareTo(BigDecimal.ZERO) > 0) {
            String taxLabel = "Tax (" + (invoice.getTaxRate() != null ? invoice.getTaxRate().toPlainString() : "0") + "%)";
            addTotalRow(totals, taxLabel, formatAmount(tax, invoice.getCurrency()), labelFont, labelFont);
        }

        BigDecimal discount = invoice.getDiscountAmount();
        if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
            String discLabel = "Discount" + (invoice.getDiscountReason() != null ? " (" + invoice.getDiscountReason() + ")" : "");
            addTotalRow(totals, discLabel, "- " + formatAmount(discount, invoice.getCurrency()), labelFont, labelFont);
        }

        // Separator row
        PdfPCell sepLabel = new PdfPCell(new Phrase(" "));
        sepLabel.setBorder(Rectangle.BOTTOM);
        sepLabel.setBorderColor(BRAND_BURGUNDY);
        sepLabel.setPadding(2);
        totals.addCell(sepLabel);
        PdfPCell sepValue = new PdfPCell(new Phrase(" "));
        sepValue.setBorder(Rectangle.BOTTOM);
        sepValue.setBorderColor(BRAND_BURGUNDY);
        sepValue.setPadding(2);
        totals.addCell(sepValue);

        addTotalRow(totals, "TOTAL DUE", formatAmount(invoice.getTotalAmount(), invoice.getCurrency()), totalFont, totalFont);

        doc.add(totals);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addPaymentInfo(Document doc, Invoice invoice) throws DocumentException {
        if ("PAID".equals(invoice.getStatus())) {
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(0x1A, 0x85, 0x32));
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK);

            Paragraph section = new Paragraph("Payment Information", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_BURGUNDY));
            doc.add(section);
            doc.add(spacer(6));

            PdfPTable payTable = new PdfPTable(2);
            payTable.setWidthPercentage(60);
            payTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            payTable.setWidths(new float[]{45, 55});

            addPayRow(payTable, "Status", "PAID", boldFont, bodyFont);
            if (invoice.getPaidAt() != null) {
                addPayRow(payTable, "Paid On", invoice.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")), bodyFont, bodyFont);
            }
            if (invoice.getPaymentMethod() != null) {
                addPayRow(payTable, "Method", invoice.getPaymentMethod(), bodyFont, bodyFont);
            }
            if (invoice.getPaymentReference() != null) {
                addPayRow(payTable, "Reference", invoice.getPaymentReference(), bodyFont, bodyFont);
            }
            if (invoice.getPaymentAmount() != null) {
                addPayRow(payTable, "Amount Paid", formatAmount(invoice.getPaymentAmount(), invoice.getCurrency()), bodyFont, bodyFont);
            }
            doc.add(payTable);
        } else {
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK);
            Paragraph section = new Paragraph("Payment Instructions", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_BURGUNDY));
            doc.add(section);
            doc.add(spacer(6));
            Paragraph instructions = new Paragraph(
                    "Please remit payment by " + (invoice.getDueDate() != null ? invoice.getDueDate().format(DATE_FMT) : "the due date") +
                    ". For payment queries, contact " + supportEmail + ".", bodyFont);
            doc.add(instructions);
        }

        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            doc.add(spacer(10));
            Font notesFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, BODY_DARK);
            Paragraph notesTitle = new Paragraph("Notes", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BODY_DARK));
            doc.add(notesTitle);
            doc.add(new Paragraph(invoice.getNotes(), notesFont));
        }
    }

    private void addPayRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell lc = new PdfPCell(new Phrase(label, labelFont));
        lc.setBorder(Rectangle.NO_BORDER);
        lc.setPadding(4);
        table.addCell(lc);

        PdfPCell vc = new PdfPCell(new Phrase(value, valueFont));
        vc.setBorder(Rectangle.NO_BORDER);
        vc.setPadding(4);
        table.addCell(vc);
    }

    private void addFooter(Document doc) throws DocumentException {
        LineSeparator sep = new LineSeparator(1f, 100f, MID_GRAY, Element.ALIGN_CENTER, 0f);
        doc.add(new Chunk(sep));
        doc.add(spacer(8));

        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(0x88, 0x88, 0x88));
        Paragraph footer = new Paragraph(
                "Thank you for your business. For billing support contact " + supportEmail +
                " | " + companyName, footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** OpenPDF has no Spacer class — use a Paragraph with spacingAfter instead. */
    private static Paragraph spacer(float pts) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(pts);
        p.setLeading(0f);
        return p;
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return currency + " 0.00";
        return currency + " " + String.format("%,.2f", amount);
    }
}
