package com.chm.myfinances.infrastructure.web.report;

import com.chm.myfinances.application.report.ReportType;
import java.util.List;

/**
 * {@code POST /api/reports}' request body (F030 spec): {@code {types: [...], filter: {...}}}.
 * {@code filter} is optional - an absent one means no filtering at all.
 */
public record ReportRequest(List<ReportType> types, ReportFilterRequest filter) {}
