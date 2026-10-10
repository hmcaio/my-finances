package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link PdfReportWriter} (F030): renders a valid, non-empty PDF byte stream. */
class PdfReportWriterTest {

  @Test
  void rendersAPdfDocumentWithATitleAndASection() {
    byte[] pdf =
        PdfReportWriter.render(
            "Test Report",
            List.of(
                new PdfReportWriter.Section(
                    "A Section", List.of("col1", "col2"), List.of(List.of("v1", "v2")))));

    assertThat(pdf).isNotEmpty();
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  @Test
  void rendersMultipleSections() {
    byte[] pdf =
        PdfReportWriter.render(
            "Test Report",
            List.of(
                new PdfReportWriter.Section("Section 1", List.of("a"), List.of(List.of("1"))),
                new PdfReportWriter.Section("Section 2", List.of("b"), List.of(List.of("2")))));

    assertThat(pdf).isNotEmpty();
  }
}
