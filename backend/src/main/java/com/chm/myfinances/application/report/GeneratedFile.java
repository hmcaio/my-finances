package com.chm.myfinances.application.report;

/**
 * One rendered report file (F030 spec): {@code fileNameWithoutExtension} is the bare slug (e.g.
 * {@code "fii-portfolio"}), with no "my-finances-"/date prefix or extension - {@link
 * ReportService}/the controller append those once, consistently, for both the bare-file and
 * zip-entry cases.
 */
public record GeneratedFile(String fileNameWithoutExtension, ReportFormat format, byte[] content) {}
