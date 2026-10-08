package com.chm.myfinances.application.investmentreport;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Dividend history (F026 spec, ADR 0023): the dedicated dividend category's own transactions,
 * optionally filtered by product and/or date range, with totals grouped by ticker and by month.
 * Reuses {@link TransactionRepository} (filtered by the dividend category and date range) and a
 * join through {@link InvestmentHoldingRepository}/{@link InvestmentProductRepository} to resolve
 * each transaction's ticker - no new persistence, same "computed on read" style as every other
 * query object in this package.
 */
@Service
public class DividendHistoryQuery {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentProductRepository productRepository;

  public DividendHistoryQuery(
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentProductRepository productRepository) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.holdingRepository = holdingRepository;
    this.productRepository = productRepository;
  }

  /**
   * Every dividend transaction within {@code from}/{@code to} (either may be {@code null}),
   * optionally restricted to one product's holdings, ordered by date descending. Empty - not an
   * error - while no dividend category exists yet.
   */
  public List<DividendRow> dividends(UUID productId, LocalDate from, LocalDate to) {
    Optional<UUID> dividendCategoryId = dividendCategoryId();
    if (dividendCategoryId.isEmpty()) {
      return List.of();
    }
    List<Transaction> transactions =
        transactionRepository.findByCategoryIdAndDateRange(dividendCategoryId.get(), from, to);
    return transactions.stream()
        .map(this::toRow)
        .filter(row -> productId == null || productId.equals(row.productId()))
        .sorted(Comparator.comparing(DividendRow::date).reversed())
        .toList();
  }

  /**
   * Dividend totals grouped by ticker, within {@code from}/{@code to} (either may be {@code null}).
   */
  public List<DividendTotalByTicker> totalsByTicker(LocalDate from, LocalDate to) {
    Map<UUID, DividendTotalByTicker> byProduct = new LinkedHashMap<>();
    for (DividendRow row : dividends(null, from, to)) {
      DividendTotalByTicker existing = byProduct.get(row.productId());
      BigDecimal amount =
          (existing == null ? BigDecimal.ZERO : existing.amount()).add(row.amount());
      byProduct.put(
          row.productId(), new DividendTotalByTicker(row.productId(), row.ticker(), amount));
    }
    return byProduct.values().stream()
        .sorted(Comparator.comparing(t -> t.ticker() == null ? "" : t.ticker()))
        .toList();
  }

  /**
   * Dividend totals grouped by month, within {@code from}/{@code to} (either may be {@code null}).
   */
  public List<DividendTotalByMonth> totalsByMonth(LocalDate from, LocalDate to) {
    Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
    for (DividendRow row : dividends(null, from, to)) {
      YearMonth month = YearMonth.from(row.date());
      byMonth.merge(month, row.amount(), BigDecimal::add);
    }
    return byMonth.entrySet().stream()
        .map(e -> new DividendTotalByMonth(e.getKey(), e.getValue()))
        .sorted(Comparator.comparing(DividendTotalByMonth::month))
        .toList();
  }

  private DividendRow toRow(Transaction transaction) {
    UUID holdingId = transaction.getInvestmentHoldingId();
    InvestmentHolding holding =
        holdingId == null ? null : holdingRepository.findById(holdingId).orElse(null);
    InvestmentProduct product =
        holding == null ? null : productRepository.findById(holding.getProductId()).orElse(null);
    return new DividendRow(
        transaction.getId(),
        transaction.getDate(),
        transaction.getAmount(),
        holdingId,
        product == null ? null : product.getId(),
        product == null ? null : product.getTicker(),
        product == null ? null : product.getName(),
        transaction.getDescription());
  }

  private Optional<UUID> dividendCategoryId() {
    return categoryRepository.findAll().stream()
        .filter(Category::isDividendCategory)
        .map(Category::getId)
        .findFirst();
  }
}
