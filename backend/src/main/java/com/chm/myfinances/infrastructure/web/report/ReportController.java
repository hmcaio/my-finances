package com.chm.myfinances.infrastructure.web.report;

import com.chm.myfinances.application.report.ReportService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the curated reports (F030 spec, PRD S6.9), replacing F013's {@code GET /api/export}.
 * One endpoint for all eleven types - generation happens in-process through {@link ReportService},
 * never a per-report-type controller.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

  private static final String ZIP_MEDIA_TYPE = "application/zip";

  private final ReportService reportService;
  private final Clock clock;

  public ReportController(ReportService reportService, Clock clock) {
    this.reportService = reportService;
    this.clock = clock;
  }

  /**
   * {@code POST /api/reports}, body {@code {types: [...], filter: {...}}}: the bare file when
   * {@code types} resolves to exactly one generated file, otherwise a {@code .zip}. {@code
   * Content-Disposition} names it {@code my-finances-<type-slug>-YYYY-MM-DD.<ext>} (a bare file) or
   * {@code my-finances-reports-YYYY-MM-DD.zip} (a zip) - the date being today. An empty/unknown
   * {@code types} list is {@code 400}.
   */
  @PostMapping
  @ApiResponse(
      responseCode = "400",
      description = "type: urn:my-finances:error:empty-report-types",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<byte[]> generate(@RequestBody ReportRequest request) {
    ReportService.Result result =
        reportService.generate(request.types(), ReportFilterRequest.toFilter(request.filter()));

    boolean isZip = result.format() == null;
    String mediaType = isZip ? ZIP_MEDIA_TYPE : result.format().contentType();
    String extension = isZip ? "zip" : result.format().extension();
    String fileName =
        "my-finances-"
            + result.fileNameWithoutExtension()
            + "-"
            + LocalDate.now(clock)
            + "."
            + extension;

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .header(HttpHeaders.CONTENT_TYPE, mediaType)
        .body(result.content());
  }
}
