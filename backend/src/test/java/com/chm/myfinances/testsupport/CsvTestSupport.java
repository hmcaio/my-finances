package com.chm.myfinances.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Naive CSV parsing shared by F030's report generator tests (same convention F013's {@code
 * DataExportServiceTest} used): fixture names/descriptions never contain commas or quotes, so
 * splitting on {@code ,}/{@code \r\n} is enough; escaping itself is {@code CsvWriterTest}'s job,
 * not any of these tests'.
 */
public final class CsvTestSupport {

  private CsvTestSupport() {}

  /** Parses a single-section CSV (one header line, then data rows) into header-keyed maps. */
  public static List<Map<String, String>> parseSingleSection(byte[] content) {
    String[] lines = new String(content, StandardCharsets.UTF_8).split("\r\n", -1);
    return parseSection(lines, 0, lines.length);
  }

  /**
   * Splits a multi-section CSV ({@link com.chm.myfinances.application.report.CsvSectionWriter}'s
   * output: every section has a non-{@code null} heading, sections separated by a blank line - the
   * convention every multi-section report generator follows) into one list of header-keyed maps per
   * section, in order. A section with no heading at all is a single-section report and parses with
   * {@link #parseSingleSection} instead.
   */
  public static List<List<Map<String, String>>> parseSections(byte[] content, int sectionCount) {
    String[] lines = new String(content, StandardCharsets.UTF_8).split("\r\n", -1);
    List<List<Map<String, String>>> sections = new ArrayList<>();
    int start = 0;
    for (int s = 0; s < sectionCount; s++) {
      int end = start;
      while (end < lines.length && !lines[end].isEmpty()) {
        end++;
      }
      // lines[start] is the section's heading line, skipped here - the header row is the next one.
      sections.add(parseSection(lines, start + 1, end));
      start = end + 1;
    }
    return sections;
  }

  private static List<Map<String, String>> parseSection(String[] lines, int start, int end) {
    if (start >= end) {
      return List.of();
    }
    String[] header = lines[start].split(",", -1);
    List<Map<String, String>> rows = new ArrayList<>();
    for (int i = start + 1; i < end; i++) {
      if (lines[i].isEmpty()) {
        continue;
      }
      String[] cells = lines[i].split(",", -1);
      Map<String, String> row = new LinkedHashMap<>();
      for (int c = 0; c < header.length && c < cells.length; c++) {
        row.put(header[c], cells[c]);
      }
      rows.add(row);
    }
    return rows;
  }
}
