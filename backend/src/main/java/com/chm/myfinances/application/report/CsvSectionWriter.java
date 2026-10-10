package com.chm.myfinances.application.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Shared multi-section CSV builder reused by every report generator's CSV output (F030): one or
 * more labeled tables, separated by a blank line, in a single file - the shape every report that
 * has more than one logical table (a detail list plus a summary block, two allocation sections, a
 * dividend list plus its ticker/month totals, ...) uses, and a plain single-section report is just
 * one {@link Section}.
 *
 * <p>A row's cells stay as whatever type the caller passes (a {@link java.math.BigDecimal} amount,
 * a {@link java.time.LocalDate}/{@link java.time.YearMonth}, or a {@code String}) rather than being
 * pre-formatted to text: {@link CsvWriter}'s formula-injection guard only applies to {@code String}
 * cells, so a pre-stringified negative amount (leading {@code -}) would otherwise be guarded into
 * text (backend {@code CLAUDE.md}'s "pass amounts, dates and ids as their own types, never
 * pre-formatted strings" rule).
 */
public final class CsvSectionWriter {

  private CsvSectionWriter() {}

  /** One table: an optional heading row above it, its column headers, and its data rows. */
  public record Section(String heading, List<String> columns, List<List<Object>> rows) {}

  public static byte[] write(List<Section> sections) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CsvWriter csv = new CsvWriter(out);
    try {
      boolean first = true;
      for (Section section : sections) {
        if (!first) {
          csv.row();
        }
        first = false;
        if (section.heading() != null) {
          csv.row(section.heading());
        }
        csv.row(section.columns().toArray());
        for (List<Object> row : section.rows()) {
          csv.row(row.toArray());
        }
      }
      csv.flush();
    } catch (IOException e) {
      throw new ReportRenderingException(e);
    }
    return out.toByteArray();
  }
}
