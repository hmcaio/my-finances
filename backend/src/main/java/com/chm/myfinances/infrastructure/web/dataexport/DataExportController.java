package com.chm.myfinances.infrastructure.web.dataexport;

import com.chm.myfinances.application.dataexport.DataExportService;
import com.chm.myfinances.application.dataexport.ExportFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the all-entity CSV export (F013, PRD S6.9). */
@RestController
@RequestMapping("/api/export")
public class DataExportController {

  private static final String ZIP_MEDIA_TYPE = "application/zip";

  private final DataExportService dataExportService;
  private final Clock clock;

  public DataExportController(DataExportService dataExportService, Clock clock) {
    this.dataExportService = dataExportService;
    this.clock = clock;
  }

  /**
   * {@code GET /api/export?dateFrom=&dateTo=&accountId=&categoryId=}: a {@code .zip} of twelve CSVs
   * as an attachment. Every filter is optional and each only affects the files that have that
   * dimension (see {@link DataExportService}). The zip is built fully in memory before anything is
   * sent, so a failure is a clean error response rather than a truncated download.
   */
  @GetMapping(produces = ZIP_MEDIA_TYPE)
  public ResponseEntity<byte[]> export(
      @RequestParam(name = "dateFrom", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateFrom,
      @RequestParam(name = "dateTo", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateTo,
      @RequestParam(name = "accountId", required = false) UUID accountId,
      @RequestParam(name = "categoryId", required = false) UUID categoryId)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    dataExportService.export(new ExportFilter(dateFrom, dateTo, accountId, categoryId), out);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("my-finances-export-" + LocalDate.now(clock) + ".zip")
                .build()
                .toString())
        .header(HttpHeaders.CONTENT_TYPE, ZIP_MEDIA_TYPE)
        .body(out.toByteArray());
  }
}
