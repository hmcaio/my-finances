package com.chm.myfinances.application.investmentreport;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The monthly per-product value series (F009 spec, PRD S6.6, rewired onto holdings by F022/ADR
 * 0020): for each product and each month in the range, the value, the month's contributions and the
 * running units - see {@link SeriesPoint}. A product's value at a point is the sum of its holdings'
 * own latest snapshots as of that point (a product can be held at more than one account, each with
 * independent snapshot history). The point in time of a month is its last day, except the current
 * month, which is evaluated at today; months after the current one are not included. Direction of a
 * trade is derived, as everywhere (ADR 0012): a transfer into one of the product's holding accounts
 * is a buy, out of one is a sell. Computed on read from raw data; nothing is derived from quantity
 * or price.
 */
@Service
public class InvestmentValueSeriesQuery {

  /** Upper bound on the requested range, so one request can't fan out over decades of months. */
  static final int MAX_MONTHS = 120;

  private final InvestmentProductRepository productRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentSnapshotRepository snapshotRepository;
  private final TransferRepository transferRepository;
  private final Clock clock;

  public InvestmentValueSeriesQuery(
      InvestmentProductRepository productRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentSnapshotRepository snapshotRepository,
      TransferRepository transferRepository,
      Clock clock) {
    this.productRepository = productRepository;
    this.holdingRepository = holdingRepository;
    this.snapshotRepository = snapshotRepository;
    this.transferRepository = transferRepository;
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
    Set<UUID> holdingAccountIds = new HashSet<>();
    List<List<InvestmentSnapshot>> snapshotsByHolding = new ArrayList<>();
    for (InvestmentHolding holding : holdings) {
      holdingAccountIds.add(holding.getAccountId());
      snapshotsByHolding.add(snapshotRepository.findByHoldingId(holding.getId()));
    }
    List<Transfer> trades = transferRepository.findByInvestmentProductId(product.getId());
    boolean hasQuantities = trades.stream().anyMatch(t -> t.getTradeDetails().quantity() != null);

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
      for (Transfer trade : trades) {
        if (trade.getDate().isAfter(point)) {
          continue;
        }
        boolean buy = holdingAccountIds.contains(trade.getToAccountId());
        if (!trade.getDate().isBefore(monthStart)) {
          contributed =
              buy ? contributed.add(trade.getAmount()) : contributed.subtract(trade.getAmount());
        }
        BigDecimal quantity = trade.getTradeDetails().quantity();
        if (quantity != null) {
          units = buy ? units.add(quantity) : units.subtract(quantity);
        }
      }
      points.add(new SeriesPoint(month, value, contributed, hasQuantities ? units : null));
    }
    return new ProductSeries(product.getId(), List.copyOf(points));
  }
}
