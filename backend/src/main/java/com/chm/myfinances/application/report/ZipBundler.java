package com.chm.myfinances.application.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zips several {@link GeneratedFile}s into one stream (F030 spec, the zip-writing half of F013's
 * generic export helper, moved here and generalized beyond CSV: an entry can be a CSV or a PDF).
 * Each entry is named the same way the bare-file response is ({@code
 * my-finances-<slug>-YYYY-MM-DD.<ext>}), so unzipping gives file names consistent with what a
 * single-type download would have produced.
 */
public final class ZipBundler {

  private ZipBundler() {}

  public static byte[] zip(List<GeneratedFile> files, LocalDate date) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (GeneratedFile file : files) {
        zip.putNextEntry(new ZipEntry(entryName(file, date)));
        zip.write(file.content());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  public static String entryName(GeneratedFile file, LocalDate date) {
    return "my-finances-"
        + file.fileNameWithoutExtension()
        + "-"
        + date
        + "."
        + file.format().extension();
  }
}
