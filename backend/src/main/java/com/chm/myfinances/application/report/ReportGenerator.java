package com.chm.myfinances.application.report;

import java.util.List;

/**
 * One report type's generation logic (F030 spec), constructor-injected with the existing
 * application services/queries it needs - never a new per-type REST endpoint or persistence query.
 * Read-only: no generator writes anything or calls the {@code AuditLog} port (ADR 0022) -
 * generating a report is never an audited action.
 *
 * <p>Returns a list rather than a single file because two report types (FII portfolio, Budgets
 * &amp; spending) render both a CSV and a PDF for the same selection (spec's "CSV + PDF") - every
 * other type's generator returns a singleton list. {@link ReportService} is the one place that
 * decides, from the total file count across every requested type, whether the response is a bare
 * file or a zip - "one file per type" in the common case, more when a type contributes more than
 * one file.
 */
public interface ReportGenerator {

  ReportType type();

  List<GeneratedFile> generate(ReportFilter filter);
}
