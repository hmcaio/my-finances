package com.chm.myfinances.application.report;

/**
 * Wraps an unexpected OpenPDF rendering failure (F030): genuinely unforeseen, so it deliberately
 * carries no {@code @ResponseStatus} - {@code GlobalExceptionHandler}'s generic 500 handles it,
 * same as any other unexpected exception ({@code docs/conventions/errors.md}: one cross-cutting
 * advice).
 */
public class ReportRenderingException extends RuntimeException {

  public ReportRenderingException(Throwable cause) {
    super("Failed to render report", cause);
  }
}
