package com.chm.myfinances.application.report;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Orchestrates report generation (F030 spec): resolves each requested {@link ReportType}'s {@link
 * ReportGenerator}, runs it, and collects every {@link GeneratedFile}. Read-only - no report writes
 * anything or calls the {@code AuditLog} port (ADR 0022), regardless of the HTTP verb the
 * controller uses.
 *
 * <p>A single generated file is returned as-is; two or more are zipped ({@link ZipBundler}). This
 * is "one file per type" in the common case, but a type whose generator contributes more than one
 * file (FII portfolio, Budgets - both CSV and PDF) also triggers a zip even when only that one type
 * was requested, since the response is then more than one file.
 */
@Service
public class ReportService {

  private final Map<ReportType, ReportGenerator> generatorsByType;
  private final Clock clock;

  public ReportService(List<ReportGenerator> generators, Clock clock) {
    this.generatorsByType = new EnumMap<>(ReportType.class);
    for (ReportGenerator generator : generators) {
      generatorsByType.put(generator.type(), generator);
    }
    this.clock = clock;
  }

  /**
   * The result of a generation request: either one bare file, or a zip of several - signaled by
   * {@code format} being {@code null} (a zip has no single {@link ReportFormat} of its own).
   */
  public record Result(String fileNameWithoutExtension, ReportFormat format, byte[] content) {}

  public Result generate(List<ReportType> types, ReportFilter filter) {
    if (types == null || types.isEmpty()) {
      throw new EmptyReportTypesException();
    }
    List<GeneratedFile> files = new ArrayList<>();
    for (ReportType type : types) {
      ReportGenerator generator = generatorsByType.get(type);
      if (generator == null) {
        // Every ReportType has a @Service generator registered; this would only trip over a
        // misconfigured context, not a bad request (an unknown enum value is already rejected by
        // Spring's own JSON deserialization before this method is ever called).
        throw new IllegalStateException("No ReportGenerator registered for " + type);
      }
      files.addAll(generator.generate(filter));
    }

    LocalDate today = LocalDate.now(clock);
    if (files.size() == 1) {
      GeneratedFile file = files.get(0);
      return new Result(file.fileNameWithoutExtension(), file.format(), file.content());
    }
    byte[] zip = zip(files, today);
    return new Result("reports", null, zip);
  }

  private static byte[] zip(List<GeneratedFile> files, LocalDate today) {
    try {
      return ZipBundler.zip(files, today);
    } catch (IOException e) {
      throw new ReportRenderingException(e);
    }
  }
}
