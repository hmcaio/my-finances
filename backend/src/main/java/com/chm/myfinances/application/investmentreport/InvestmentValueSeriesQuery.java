package com.chm.myfinances.application.investmentreport;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The monthly per-product value series (F009 spec, PRD S6.6, rewired onto holdings by F022/ADR
 * 0020): for each product and each month in the range, the value, the month's contributions and the
 * running units - see {@link SeriesPoint}. A product's value at a point is the sum of its holdings'
 * own latest snapshots as of that point (a product can be held at more than one account, each with
 * independent snapshot history). The point in time of a month is its last day, except the current
 * month, which is evaluated at today; months after the current one are not included. Direction of a
 * line is its own {@code side} (F027, ADR 0024, superseding the old "a transfer into a holding
 * account is a buy" derivation): BUY adds to contributed/units, SELL subtracts. Computed on read
 * from raw data; nothing is derived from quantity or price.
 */
@Service
public class InvestmentValueSeriesQuery {

  /** Upper bound on the requested range, so one request can't fan out over decades of months. */
  static final int MAX_MONTHS = 120;

  private final InvestmentProductRepository productRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentSnapshotRepository snapshotRepository;
  private final TransferTradeLineRepository tradeLineRepository;
  private final Clock clock;

  public InvestmentValueSeriesQuery(
      InvestmentProductRepository productRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentSnapshotRepository snapshotRepository,
      TransferTradeLineRepository tradeLineRepository,
      Clock clock) {
    this.productRepository = productRepository;
    this.holdingRepository = holdingRepository;
    this.snapshotRepository = snapshotRepository;
    this.tradeLineRepository = tradeLineRepository;
    this.clock = clock;
  }

  /**
   * One series per product, or just {@code productId}'s when given (404 if unknown), each with a
   * point per month from {@code from} to {@code to} inclusive (clamped to the current month).
   */
  public List<ProductSeries> series(YearMonth from, YearMonth to, UUID productId) {
    if (from.isAfter(to)) {
      throw new InvalidValueSeriesRangeException("from must not be after to");
    }
    if (from.until(to, ChronoUnit.MONTHS) >= MAX_MONTHS) {
      throw new InvalidValueSeriesRangeException("range must not exceed " + MAX_MONTHS + " months");
    }
    List<InvestmentProduct> products =
        productId == null
            ? productRepository.findAll().stream()
                .sorted(
                    Comparator.comparing(InvestmentProduct::getName)
                        .thenComparing(InvestmentProduct::getId))
                .toList()
            : List.of(
                productRepository
                    .findById(productId)
                    .orElseThrow(() -> new InvestmentProductNotFoundException(productId)));

    LocalDate today = LocalDate.now(clock);
    YearMonth last = to.isAfter(YearMonth.from(today)) ? YearMonth.from(today) : to;
    return products.stream().map(product -> seriesOf(product, from, last, today)).toList();
  }

  private ProductSeries seriesOf(
      InvestmentProduct product, YearMonth from, YearMonth last, LocalDate today) {
    List<InvestmentHolding> holdings = holdingRepository.findByProductId(product.getId());
    List<List<InvestmentSnapshot>> snapshotsByHolding = new ArrayList<>();
    for (InvestmentHolding holding : holdings) {
      snapshotsByHolding.add(snapshotRepository.findByHoldingId(holding.getId()));
    }
    List<TransferTradeLine> lines = tradeLineRepository.findByProductId(product.getId());

    List<SeriesPoint> points = new ArrayList<>();
    for (YearMonth month = from; !month.isAfter(last); month = month.plusMonths(1)) {
      LocalDate monthStart = month.atDay(1);
      LocalDate point = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();

      BigDecimal value = null;
      for (List<InvestmentSnapshot> holdingSnapshots : snapshotsByHolding) {
        BigDecimal holdingValue =
            holdingSnapshots.stream() // most recent first
                .filter(s -> !s.getDate().isAfter(point))
                .findFirst()
                .map(InvestmentSnapshot::getBalance)
                .orElse(null);
        if (holdingValue != null) {
          value = (value == null ? BigDecimal.ZERO : value).add(holdingValue);
        }
      }

      BigDecimal contributed = BigDecimal.ZERO;
      BigDecimal units = BigDecimal.ZERO;
      for (TransferTradeLine line : lines) {
        if (line.date().isAfter(point)) {
          continue;
        }
        boolean buy = line.side() == TradeSide.BUY;
        BigDecimal total = line.quantity().multiply(line.unitPrice());
        if (!line.date().isBefore(monthStart)) {
          contributed = buy ? contributed.add(total) : contributed.subtract(total);
        }
        units = buy ? units.add(line.quantity()) : units.subtract(line.quantity());
      }
      points.add(new SeriesPoint(month, value, contributed, lines.isEmpty() ? null : units));
    }
    return new ProductSeries(product.getId(), List.copyOf(points));
  }
}
