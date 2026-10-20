package com.chm.myfinances.application.report;

/** The file format a {@link GeneratedFile} is written in (F030 spec). */
public enum ReportFormat {
  CSV("csv", "text/csv"),
  PDF("pdf", "application/pdf");

  private final String extension;
  private final String contentType;

  ReportFormat(String extension, String contentType) {
    this.extension = extension;
    this.contentType = contentType;
  }

  public String extension() {
    return extension;
  }

  public String contentType() {
    return contentType;
  }
}
