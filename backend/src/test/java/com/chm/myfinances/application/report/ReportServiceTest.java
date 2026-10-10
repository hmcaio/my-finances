package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.testsupport.TestClocks;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ReportService} (F030 spec), written first (ADR 0004): one selected type whose
 * generator yields one file returns that bare file; several types - or a single type whose
 * generator yields more than one file - zip; an empty {@code types} list is rejected.
 */
class ReportServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  /** A minimal stub generator - every real generator already has its own dedicated test. */
  private static StubGenerator stub(ReportType type, String... slugsAndContents) {
    return new StubGenerator(type, slugsAndContents);
  }

  private static final class StubGenerator implements ReportGenerator {
    private final ReportType type;
    private final List<GeneratedFile> files;

    StubGenerator(ReportType type, String... content) {
      this.type = type;
      this.files =
          List.of(
              new GeneratedFile(
                  type.slug(), ReportFormat.CSV, content[0].getBytes(StandardCharsets.UTF_8)));
    }

    StubGenerator(ReportType type, List<GeneratedFile> files) {
      this.type = type;
      this.files = files;
    }

    @Override
    public ReportType type() {
      return type;
    }

    @Override
    public List<GeneratedFile> generate(ReportFilter filter) {
      return files;
    }
  }

  @Test
  void aSingleSelectedTypeReturnsItsBareFileNotAZip() {
    ReportService service =
        new ReportService(
            List.of(stub(ReportType.ANNUAL_SUMMARY, "year,total\n2026,100")),
            TestClocks.fixedAtStartOf(TODAY));

    ReportService.Result result =
        service.generate(List.of(ReportType.ANNUAL_SUMMARY), ReportFilter.none());

    assertThat(result.format()).isEqualTo(ReportFormat.CSV);
    assertThat(result.fileNameWithoutExtension()).isEqualTo("annual-summary");
    assertThat(new String(result.content(), StandardCharsets.UTF_8)).contains("2026,100");
  }

  @Test
  void twoSelectedTypesProduceAZipWithOneCorrectlyNamedFilePerType() throws IOException {
    ReportService service =
        new ReportService(
            List.of(
                stub(ReportType.ANNUAL_SUMMARY, "annual content"),
                stub(ReportType.DIVIDEND_INCOME, "dividend content")),
            TestClocks.fixedAtStartOf(TODAY));

    ReportService.Result result =
        service.generate(
            List.of(ReportType.ANNUAL_SUMMARY, ReportType.DIVIDEND_INCOME), ReportFilter.none());

    assertThat(result.format()).isNull();
    Set<String> entryNames = zipEntryNames(result.content());
    assertThat(entryNames)
        .containsExactlyInAnyOrder(
            "my-finances-annual-summary-" + TODAY + ".csv",
            "my-finances-dividend-income-" + TODAY + ".csv");
  }

  @Test
  void aSingleTypeWhoseGeneratorYieldsTwoFilesStillProducesAZip() throws IOException {
    List<GeneratedFile> twoFiles =
        List.of(
            new GeneratedFile(
                "fii-portfolio", ReportFormat.CSV, "csv".getBytes(StandardCharsets.UTF_8)),
            new GeneratedFile(
                "fii-portfolio", ReportFormat.PDF, "pdf".getBytes(StandardCharsets.UTF_8)));
    ReportService service =
        new ReportService(
            List.of(new StubGenerator(ReportType.FII_PORTFOLIO, twoFiles)),
            TestClocks.fixedAtStartOf(TODAY));

    ReportService.Result result =
        service.generate(List.of(ReportType.FII_PORTFOLIO), ReportFilter.none());

    assertThat(result.format()).isNull();
    assertThat(zipEntryNames(result.content()))
        .containsExactlyInAnyOrder(
            "my-finances-fii-portfolio-" + TODAY + ".csv",
            "my-finances-fii-portfolio-" + TODAY + ".pdf");
  }

  @Test
  void anEmptyTypesListIsRejected() {
    ReportService service = new ReportService(List.of(), TestClocks.fixedAtStartOf(TODAY));

    assertThatThrownBy(() -> service.generate(List.of(), ReportFilter.none()))
        .isInstanceOf(EmptyReportTypesException.class);
    assertThatThrownBy(() -> service.generate(null, ReportFilter.none()))
        .isInstanceOf(EmptyReportTypesException.class);
  }

  private static Set<String> zipEntryNames(byte[] zipBytes) throws IOException {
    Set<String> names = new java.util.HashSet<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        names.add(entry.getName());
      }
    }
    return names;
  }
}
