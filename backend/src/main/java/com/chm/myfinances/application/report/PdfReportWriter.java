package com.chm.myfinances.application.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Small table-rendering helper on top of OpenPDF (F030 spec's Decisions: OpenPDF/LibrePDF, not
 * iText - iText's current major is AGPL-3.0, a poor fit for a project that publishes Docker
 * images), reused by the two reports that get a PDF rendering (FII portfolio, Budgets &amp;
 * spending). One title, then one or more labeled table {@link Section}s, each rendered as a simple
 * header row plus data rows - no styling beyond what a printed report needs.
 */
public final class PdfReportWriter {

  private PdfReportWriter() {}

  /** One table within the PDF: an optional heading above it, its column headers, and its rows. */
  public record Section(String heading, List<String> columns, List<List<String>> rows) {}

  public static byte[] render(String title, List<Section> sections) {
    Document document = new Document();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(document, out);
      document.open();
      document.add(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
      for (Section section : sections) {
        document.add(new Paragraph(" "));
        if (section.heading() != null) {
          document.add(
              new Paragraph(
                  section.heading(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        }
        document.add(table(section));
      }
      document.close();
    } catch (DocumentException e) {
      throw new ReportRenderingException(e);
    }
    return out.toByteArray();
  }

  private static PdfPTable table(Section section) throws DocumentException {
    PdfPTable table = new PdfPTable(Math.max(section.columns().size(), 1));
    table.setWidthPercentage(100);
    Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    for (String column : section.columns()) {
      PdfPCell cell = new PdfPCell(new Paragraph(column, headerFont));
      cell.setHorizontalAlignment(Element.ALIGN_LEFT);
      table.addCell(cell);
    }
    Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
    for (List<String> row : section.rows()) {
      for (String value : row) {
        table.addCell(new Paragraph(value == null ? "" : value, cellFont));
      }
    }
    return table;
  }
}
