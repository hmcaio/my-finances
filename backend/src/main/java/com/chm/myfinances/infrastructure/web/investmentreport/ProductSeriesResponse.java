package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.ProductSeries;
import com.chm.myfinances.application.investmentreport.SeriesPoint;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * One product's monthly series of {@code GET /api/investments/value-series} (F009 spec). {@code
 * month} is a {@code yyyy-MM} string on the wire (see {@code OpenApiConfig}). {@code value} is
 * {@code null} before the first snapshot; {@code units} is {@code null} when the product has no
 * recorded quantities.
 */
public record ProductSeriesResponse(UUID productId, List<SeriesPointResponse> points) {

  /** One month of the series. */
  public record SeriesPointResponse(
      YearMonth month, BigDecimal value, BigDecimal contributed, BigDecimal units) {

    static SeriesPointResponse from(SeriesPoint point) {
      return new SeriesPointResponse(
          point.month(), point.value(), point.contributed(), point.units());
    }
  }

  public static ProductSeriesResponse from(ProductSeries series) {
    return new ProductSeriesResponse(
        series.productId(), series.points().stream().map(SeriesPointResponse::from).toList());
  }
}
