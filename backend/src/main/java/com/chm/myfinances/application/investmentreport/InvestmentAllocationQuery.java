package com.chm.myfinances.application.investmentreport;

import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The allocation view (F009 spec, PRD S6.6, rewired onto holdings by F022/ADR 0020): a product's
 * value is now the sum of its holdings' latest snapshots (a product can be held at more than one
 * account), grouped by category or by category then sub-category. Computed on read. A product with
 * a {@code 0} or missing total adds nothing to a group's total; the group's {@code needsSnapshot}
 * is true when any of its products has at least one stale holding, and a group whose only
 * contributors are stale products with no value yet is still emitted (with a {@code 0} total) so
 * the freshness warning isn't silently lost.
 *
 * <p>Grouping uses each product's <em>current</em> category/sub-category, so reclassifying a
 * product regroups its past allocation (ADR 0012). Category totals equal the sum of their
 * sub-category rows by construction.
 *
 * <p>{@link AllocationGrouping#ACCOUNT} (F023) sums holdings directly into their account, with no
 * product roll-up: each holding's own latest snapshot is added to its account's total, since value
 * is per holding, not per product (a product held at two accounts contributes to both).
 */
@Service
public class InvestmentAllocationQuery {

  private final InvestmentProductRepository productRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentCategoryRepository categoryRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final AccountRepository accountRepository;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final InvestmentSnapshotFreshnessQuery freshnessQuery;

  public InvestmentAllocationQuery(
      InvestmentProductRepository productRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentCategoryRepository categoryRepository,
      InvestmentSubcategoryRepository subcategoryRepository,
      AccountRepository accountRepository,
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      InvestmentSnapshotFreshnessQuery freshnessQuery) {
    this.productRepository = productRepository;
    this.holdingRepository = holdingRepository;
    this.categoryRepository = categoryRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.accountRepository = accountRepository;
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.freshnessQuery = freshnessQuery;
  }

  /**
   * Slices ordered by category name, then sub-category name (the null slice last); {@code ACCOUNT}
   * slices are ordered by account name.
   */
  public List<AllocationRow> allocation(LocalDate asOfDate, AllocationGrouping grouping) {
    if (grouping == AllocationGrouping.ACCOUNT) {
      return allocationByAccount(asOfDate);
    }
    return allocationByCategory(asOfDate, grouping);
  }

  private List<AllocationRow> allocationByAccount(LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latestByHolding = latestSnapshotQuery.latestByHolding(asOfDate);
    Set<UUID> staleHoldings = freshnessQuery.staleHoldingIds(asOfDate);
    Map<UUID, String> accountNames =
        accountRepository.findAll().stream()
            .collect(Collectors.toMap(Account::getId, Account::getName));

    Map<UUID, Accumulator> groups = new LinkedHashMap<>();
    for (InvestmentHolding holding : holdingRepository.findAll()) {
      InvestmentSnapshot snapshot = latestByHolding.get(holding.getId());
      BigDecimal value = snapshot != null ? snapshot.getBalance() : BigDecimal.ZERO;
      boolean isStale = staleHoldings.contains(holding.getId());
      if (value.signum() == 0 && !isStale) {
        continue;
      }
      Accumulator accumulator =
          groups.computeIfAbsent(holding.getAccountId(), k -> new Accumulator());
      accumulator.total = accumulator.total.add(value);
      accumulator.needsSnapshot |= isStale;
    }

    Comparator<AllocationRow> order = Comparator.comparing(AllocationRow::accountName);
    return groups.entrySet().stream()
        .map(
            entry ->
                new AllocationRow(
                    null,
                    null,
                    null,
                    null,
                    entry.getKey(),
                    accountNames.get(entry.getKey()),
                    entry.getValue().total,
                    entry.getValue().needsSnapshot))
        .sorted(order)
        .toList();
  }

  private List<AllocationRow> allocationByCategory(
      LocalDate asOfDate, AllocationGrouping grouping) {
    Map<UUID, InvestmentSnapshot> latestByHolding = latestSnapshotQuery.latestByHolding(asOfDate);
    Set<UUID> staleHoldings = freshnessQuery.staleHoldingIds(asOfDate);
    Map<UUID, String> categoryNames =
        categoryRepository.findAll().stream()
            .collect(Collectors.toMap(InvestmentCategory::getId, InvestmentCategory::getName));
    Map<UUID, String> subcategoryNames =
        subcategoryRepository.findAll().stream()
            .collect(
                Collectors.toMap(InvestmentSubcategory::getId, InvestmentSubcategory::getName));

    Map<GroupKey, Accumulator> groups = new LinkedHashMap<>();
    for (InvestmentProduct product : productRepository.findAll()) {
      List<InvestmentHolding> holdings = holdingRepository.findByProductId(product.getId());
      BigDecimal value = BigDecimal.ZERO;
      boolean isStale = false;
      for (InvestmentHolding holding : holdings) {
        InvestmentSnapshot snapshot = latestByHolding.get(holding.getId());
        if (snapshot != null) {
          value = value.add(snapshot.getBalance());
        }
        if (staleHoldings.contains(holding.getId())) {
          isStale = true;
        }
      }
      if (value.signum() == 0 && !isStale) {
        continue;
      }
      UUID subcategoryId =
          grouping == AllocationGrouping.SUBCATEGORY ? product.getInvestmentSubcategoryId() : null;
      Accumulator accumulator =
          groups.computeIfAbsent(
              new GroupKey(product.getInvestmentCategoryId(), subcategoryId),
              key -> new Accumulator());
      accumulator.total = accumulator.total.add(value);
      accumulator.needsSnapshot |= isStale;
    }

    Comparator<AllocationRow> order =
        Comparator.comparing(AllocationRow::categoryName)
            .thenComparing(
                AllocationRow::subcategoryName, Comparator.nullsLast(Comparator.naturalOrder()));
    return groups.entrySet().stream()
        .map(
            entry ->
                new AllocationRow(
                    entry.getKey().categoryId(),
                    categoryNames.get(entry.getKey().categoryId()),
                    entry.getKey().subcategoryId(),
                    entry.getKey().subcategoryId() == null
                        ? null
                        : subcategoryNames.get(entry.getKey().subcategoryId()),
                    null,
                    null,
                    entry.getValue().total,
                    entry.getValue().needsSnapshot))
        .sorted(order)
        .toList();
  }

  private record GroupKey(UUID categoryId, UUID subcategoryId) {
    GroupKey {
      Objects.requireNonNull(categoryId, "categoryId must not be null");
    }
  }

  private static final class Accumulator {
    private BigDecimal total = BigDecimal.ZERO;
    private boolean needsSnapshot;
  }
}
