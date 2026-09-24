package com.chm.myfinances.application.dataexport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 4180 CSV writer for F013: UTF-8, CRLF line endings, a cell is quoted only when it
 * contains a comma, quote, CR or LF.
 *
 * <p>Formula-injection guard: a {@link String} cell (names, descriptions, notes - the only
 * user-typed values) starting with {@code =}, {@code +}, {@code -}, {@code @}, tab or CR gets a
 * leading {@code '} so Excel/Sheets/LibreOffice show it as text instead of evaluating it. Ids,
 * dates, enums and numbers are never guarded - a negative amount must stay a number - and {@code
 * null} is an empty cell. Closing the writer's stream is the caller's job ({@link #flush()} only).
 */
public final class CsvWriter {

  private final Writer writer;

  public CsvWriter(OutputStream out) {
    this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
  }

  public void row(Object... cells) throws IOException {
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        writer.write(',');
      }
      writer.write(escape(format(cells[i])));
    }
    writer.write("\r\n");
  }

  public void flush() throws IOException {
    writer.flush();
  }

  private static String format(Object cell) {
    if (cell == null) {
      return "";
    }
    if (cell instanceof BigDecimal number) {
      return number.toPlainString();
    }
    if (cell instanceof String text) {
      return guardFormula(text);
    }
    return cell.toString();
  }

  private static String guardFormula(String text) {
    if (text.isEmpty()) {
      return text;
    }
    return switch (text.charAt(0)) {
      case '=', '+', '-', '@', '\t', '\r' -> "'" + text;
      default -> text;
    };
  }

  private static String escape(String value) {
    boolean needsQuotes =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
  }
}
