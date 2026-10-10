package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.testsupport.CsvTestSupport;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CsvSectionWriter} (F030), written first (ADR 0004): a {@code null} cell stays
 * empty rather than being formula-guarded, and a negative {@link BigDecimal} amount is never
 * mistaken for guardable text (backend {@code CLAUDE.md}'s "pass amounts as their own types" rule)
 * - the reason this writer takes typed {@code Object} rows instead of pre-stringified ones.
 */
class CsvSectionWriterTest {

  @Test
  void aNullCellIsEmptyAndANegativeAmountIsNeverGuarded() {
    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section(
                    null,
                    List.of("name", "amount", "note"),
                    List.of(Arrays.asList("Refund", new BigDecimal("-50.00"), null)))));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(csv);
    assertThat(rows.get(0).get("amount")).isEqualTo("-50.00");
    assertThat(rows.get(0).get("note")).isEmpty();
  }

  @Test
  void multipleSectionsAreSeparatedByABlankLineWithTheirOwnHeading() {
    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section("FIRST", List.of("a"), List.of(List.of("1"))),
                new CsvSectionWriter.Section("SECOND", List.of("b"), List.of(List.of("2")))));

    List<List<Map<String, String>>> sections = CsvTestSupport.parseSections(csv, 2);
    assertThat(sections.get(0).get(0).get("a")).isEqualTo("1");
    assertThat(sections.get(1).get(0).get("b")).isEqualTo("2");
  }
}
