package com.chm.myfinances.application.investmentreport;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The four FII allocation slices (F026 spec, ADR 0023): actual/planned, by ticker/by segment.
 * Actual reuses {@link FiiPortfolioQuery}'s {@code currentValue} per product (the existing,
 * unchanged valuation source, ADR 0012), restricted to the FII sub-category, percentage of the
 * FII-only total. Planned reads the current {@link AllocationPlanVersion}'s entries directly. A
 * {@code null} segment groups as "No segment" in both segment charts.
 */
@Service
public class FiiAllocationQuery {

  private static final String NO_SEGMENT_LABEL = "No segment";
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final FiiPortfolioQuery portfolioQuery;
  private final AllocationPlanService allocationPlanService;
  private final InvestmentProductRepository productRepository;
  private final InvestmentSegmentRepository segmentRepository;
  private final Clock clock;

  public FiiAllocationQuery(
      FiiPortfolioQuery portfolioQuery,
      AllocationPlanService allocationPlanService,
      InvestmentProductRepository productRepository,
      InvestmentSegmentRepository segmentRepository,
      Clock clock) {
    this.portfolioQuery = portfolioQuery;
    this.allocationPlanService = allocationPlanService;
    this.productRepository = productRepository;
    this.segmentRepository = segmentRepository;
    this.clock = clock;
  }

  public List<FiiAllocationRow> allocation(FiiAllocationBasis basis, FiiAllocationGroupBy groupBy) {
    return switch (basis) {
      case ACTUAL -> groupBy == FiiAllocationGroupBy.TICKER ? actualByTicker() : actualBySegment();
      case PLANNED ->
          groupBy == FiiAllocationGroupBy.TICKER ? plannedByTicker() : plannedBySegment();
    };
  }

  private List<FiiPortfolioRow> fiiRowsWithValue() {
    return portfolioQuery.portfolio(InvestmentProductStatus.ALL).stream()
        .filter(row -> row.currentValue().signum() != 0)
        .toList();
  }

  private List<FiiAllocationRow> actualByTicker() {
    List<FiiPortfolioRow> rows = fiiRowsWithValue();
    BigDecimal total =
        rows.stream().map(FiiPortfolioRow::currentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() == 0) {
      return List.of();
    }
    return rows.stream()
        .map(
            row ->
                new FiiAllocationRow(
                    row.productId(),
                    row.ticker() != null ? row.ticker() : row.name(),
                    row.currentValue(),
                    percentageOf(row.currentValue(), total)))
        .sorted(Comparator.comparing(FiiAllocationRow::label))
        .toList();
  }

  private List<FiiAllocationRow> actualBySegment() {
    List<FiiPortfolioRow> rows = fiiRowsWithValue();
    BigDecimal total =
        rows.stream().map(FiiPortfolioRow::currentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() == 0) {
      return List.of();
    }
    Map<UUID, String> segmentNames = segmentNames();
    Map<UUID, BigDecimal> bySegment = new LinkedHashMap<>();
    for (FiiPortfolioRow row : rows) {
      bySegment.merge(row.segmentId(), row.currentValue(), BigDecimal::add);
    }
    return bySegment.entrySet().stream()
        .map(
            e ->
                new FiiAllocationRow(
                    e.getKey(),
                    e.getKey() == null
                        ? NO_SEGMENT_LABEL
                        : segmentNames.getOrDefault(e.getKey(), ""),
                    e.getValue(),
                    percentageOf(e.getValue(), total)))
        .sorted(Comparator.comparing(FiiAllocationRow::label))
        .toList();
  }

  private List<FiiAllocationRow> plannedByTicker() {
    Optional<AllocationPlanVersion> current =
        allocationPlanService.getCurrent(YearMonth.now(clock));
    if (current.isEmpty()) {
      return List.of();
    }
    return current.get().getEntries().stream()
        .map(
            entry -> {
              InvestmentProduct product =
                  productRepository.findById(entry.investmentProductId()).orElse(null);
              String label =
                  product == null
                      ? entry.investmentProductId().toString()
                      : (product.getTicker() != null ? product.getTicker() : product.getName());
              return new FiiAllocationRow(
                  entry.investmentProductId(), label, null, entry.targetPercentage());
            })
        .sorted(Comparator.comparing(FiiAllocationRow::label))
        .toList();
  }

  private List<FiiAllocationRow> plannedBySegment() {
    Optional<AllocationPlanVersion> current =
        allocationPlanService.getCurrent(YearMonth.now(clock));
    if (current.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> segmentNames = segmentNames();
    Map<UUID, BigDecimal> bySegment = new LinkedHashMap<>();
    for (AllocationPlanEntry entry : current.get().getEntries()) {
      UUID segmentId =
          productRepository
              .findById(entry.investmentProductId())
              .map(InvestmentProduct::getSegmentId)
              .orElse(null);
      bySegment.merge(segmentId, entry.targetPercentage(), BigDecimal::add);
    }
    return bySegment.entrySet().stream()
        .map(
            e ->
                new FiiAllocationRow(
                    e.getKey(),
                    e.getKey() == null
                        ? NO_SEGMENT_LABEL
                        : segmentNames.getOrDefault(e.getKey(), ""),
                    null,
                    e.getValue()))
        .sorted(Comparator.comparing(FiiAllocationRow::label))
        .toList();
  }

  private Map<UUID, String> segmentNames() {
    Map<UUID, String> names = new LinkedHashMap<>();
    for (InvestmentSegment segment : segmentRepository.findAll()) {
      names.put(segment.getId(), segment.getName());
    }
    return names;
  }

  private static BigDecimal percentageOf(BigDecimal value, BigDecimal total) {
    return value.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
  }
}
