package com.shopmanagement.crmservice.service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmQuotationRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class QuotationPdfService {

  private final CrmQuotationRepository quotationRepository;

  public QuotationPdfService(CrmQuotationRepository quotationRepository) {
    this.quotationRepository = quotationRepository;
  }

  @Transactional(readOnly = true)
  public byte[] renderPdf(Long id) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity q =
        quotationRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));

    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document doc = new Document();
      PdfWriter.getInstance(doc, out);
      doc.open();

      Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
      Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
      Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

      doc.add(new Paragraph("SugamFlow CRM — Tax Quotation", title));
      doc.add(new Paragraph(" ", normal));
      doc.add(new Paragraph("Quote: " + q.getQuoteNumber() + "  |  Status: " + q.getStatus(), bold));
      doc.add(new Paragraph("Customer: " + nullSafe(q.getCustomerName()), normal));
      doc.add(new Paragraph("GSTIN: " + nullSafe(q.getCustomerGstin()), normal));
      doc.add(
          new Paragraph(
              "Place of supply: "
                  + nullSafe(q.getPlaceOfSupply())
                  + "  |  Seller state: "
                  + nullSafe(q.getSellerStateCode())
                  + "  |  Buyer state: "
                  + nullSafe(q.getBuyerStateCode()),
              normal));
      doc.add(new Paragraph(" ", normal));

      PdfPTable table = new PdfPTable(5);
      table.setWidthPercentage(100);
      table.addCell(header("Description", bold));
      table.addCell(header("HSN", bold));
      table.addCell(header("Qty", bold));
      table.addCell(header("Rate", bold));
      table.addCell(header("Taxable", bold));

      List<Map<String, Object>> lines = q.getLinesJson() == null ? List.of() : q.getLinesJson();
      for (Map<String, Object> line : lines) {
        table.addCell(cell(String.valueOf(line.getOrDefault("description", "")), normal));
        table.addCell(cell(String.valueOf(line.getOrDefault("hsn", "")), normal));
        table.addCell(cell(String.valueOf(line.getOrDefault("qty", "")), normal));
        table.addCell(cell(String.valueOf(line.getOrDefault("unitPrice", "")), normal));
        table.addCell(cell(String.valueOf(line.getOrDefault("taxable", "")), normal));
      }
      doc.add(table);
      doc.add(new Paragraph(" ", normal));
      doc.add(new Paragraph("Taxable: ₹" + q.getTaxableAmount(), normal));
      doc.add(new Paragraph("CGST: ₹" + q.getCgstAmount(), normal));
      doc.add(new Paragraph("SGST: ₹" + q.getSgstAmount(), normal));
      doc.add(new Paragraph("IGST: ₹" + q.getIgstAmount(), normal));
      doc.add(new Paragraph("Total: ₹" + q.getTotalAmount(), bold));
      if (q.getPaymentLinkUrl() != null) {
        doc.add(new Paragraph("Payment link: " + q.getPaymentLinkUrl(), normal));
      }
      if (q.getTerms() != null) {
        doc.add(new Paragraph(" ", normal));
        doc.add(new Paragraph("Terms: " + q.getTerms(), normal));
      }
      doc.close();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF generation failed", ex);
    }
  }

  private static String nullSafe(String v) {
    return v == null || v.isBlank() ? "-" : v;
  }

  private static PdfPCell header(String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(4);
    return cell;
  }

  private static PdfPCell cell(String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(3);
    return cell;
  }
}
