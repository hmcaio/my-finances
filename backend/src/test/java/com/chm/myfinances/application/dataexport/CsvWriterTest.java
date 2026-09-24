package com.chm.myfinances.application.dataexport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

  private static String write(Object[]... rows) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CsvWriter writer = new CsvWriter(out);
    for (Object[] row : rows) {
      writer.row(row);
    }
    writer.flush();
    return out.toString(StandardCharsets.UTF_8);
  }

  @Test
  void writesRowsWithCrlfLineEndings() throws IOException {
    assertThat(write(new Object[] {"a", "b"}, new Object[] {"c", "d"})).isEqualTo("a,b\r\nc,d\r\n");
  }

  @Test
  void quotesCellsWithCommasQuotesAndNewlines() throws IOException {
    assertThat(write(new Object[] {"a,b", "say \"hi\"", "line1\nline2", "cr\rx"}))
        .isEqualTo("\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\",\"cr\rx\"\r\n");
  }

  @Test
  void nullBecomesAnEmptyCell() throws IOException {
    assertThat(write(new Object[] {"a", null, "c"})).isEqualTo("a,,c\r\n");
  }

  @Test
  void formatsNonTextValuesWithoutTheFormulaGuard() throws IOException {
    assertThat(
            write(
                new Object[] {
                  new BigDecimal("-12.50"),
                  new BigDecimal("1E+2"),
                  LocalDate.of(2026, 3, 9),
                  YearMonth.of(2026, 3),
                  true
                }))
        .isEqualTo("-12.50,100,2026-03-09,2026-03,true\r\n");
  }

  @Test
  void guardsTextThatSpreadsheetsWouldReadAsAFormula() throws IOException {
    assertThat(write(new Object[] {"=SUM(A1)", "+1", "-1", "@cmd", "\tx", "\rx"}))
        .isEqualTo("'=SUM(A1),'+1,'-1,'@cmd,'\tx,\"'\rx\"\r\n");
  }

  @Test
  void leavesOrdinaryTextAlone() throws IOException {
    assertThat(write(new Object[] {"Groceries", "a=b", "café"}))
        .isEqualTo("Groceries,a=b,café\r\n");
  }
}
