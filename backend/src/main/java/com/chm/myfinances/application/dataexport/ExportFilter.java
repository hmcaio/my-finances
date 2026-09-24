package com.chm.myfinances.application.dataexport;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filters for the data export (F013, PRD S6.9). Every field is nullable - {@code null}
 * means "no constraint" - and each one only affects the files that have that dimension (see {@link
 * DataExportService}); reference-only files are always exported in full. {@code dateFrom}/{@code
 * dateTo} are inclusive.
 */
public record ExportFilter(LocalDate dateFrom, LocalDate dateTo, UUID accountId, UUID categoryId) {

  /** No filtering at all - a full export. */
  public static ExportFilter none() {
    return new ExportFilter(null, null, null, null);
  }
}
