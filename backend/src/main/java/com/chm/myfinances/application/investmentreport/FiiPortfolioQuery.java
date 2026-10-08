package com.chm.myfinances.application.investmentreport;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The FII portfolio summary (F026 spec, ADR 0023): for every {@code InvestmentProduct} classified
 * under the "REITs (FIIs)" sub-category, {@code cotasHeld}/{@code amountContributed} are computed
 * on read the same way {@code InvestmentValueSeriesQuery.seriesOf} already derives {@code units}/
 * {@code contributed} - reusing {@code TransferRepository.findByInvestmentProductId} and the
 * trade's quantity/amount, direction via holding account membership - but as a running total over
 * all trades to date, not bucketed by month. {@code currentValue}/{@code needsSnapshot} reuse
 * {@code LatestInvestmentSnapshotQuery}/{@code InvestmentSnapshotFreshnessQuery}, summed per
 * product across its holdings. Nothing here is price-derived (PRD S3/S9).
 */
@Service
public class FiiPortfolioQuery {

  private final InvestmentProductRepository productRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final TransferRepository transferRepository;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final InvestmentSnapshotFreshnessQuery freshnessQuery;
  private final Clock clock;

  public FiiPortfolioQuery(
      InvestmentProductRepository productRepository,
      InvestmentSubcategoryRepository subcategoryRepository,
      InvestmentHoldingRepository holdingRepository,
      TransferRepository transferRepository,
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      InvestmentSnapshotFreshnessQuery freshnessQuery,
      Clock clock) {
    this.productRepository = productRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.holdingRepository = holdingRepository;
    this.transferRepository = transferRepository;
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.freshnessQuery = freshnessQuery;
    this.clock = clock;
  }

  /**
   * One row per FII product matching {@code status} (F023's status-filter pattern), ordered by
   * ticker (falling back to name for an untickered product). {@code asOf} drives both {@code
   * cotasHeld}/{@code amountContributed} (trades after it are excluded) and {@code
   * currentValue}/{@code latestSnapshotDate} (the latest snapshot on or before it). {@code
   * needsSnapshot} stays a "today" concept (Addendum - Month Selector): it is only ever computed -
   * and can only ever be {@code true} - when {@code asOf} is today; a past {@code asOf} always
   * reports {@code false} rather than being recomputed against that past date.
   */
  public List<FiiPortfolioRow> portfolio(InvestmentProductStatus status, LocalDate asOf) {
    LocalDate today = LocalDate.now(clock);
    Set<UUID> fiiSubcategoryIds = fiiSubcategoryIds();
    Set<UUID> staleHoldingIds =
        asOf.equals(today) ? freshnessQuery.staleHoldingIds(today) : Set.of();

    List<FiiPortfolioRow> rows = new ArrayList<>();
    for (InvestmentProduct product : productRepository.findAll()) {
      if (!fiiSubcategoryIds.contains(product.getInvestmentSubcategoryId())) {
        continue;
      }
      List<InvestmentHolding> holdings = holdingRepository.findByProductId(product.getId());
      boolean hasOpenHolding = holdings.stream().anyMatch(h -> !h.isClosed());
      if (!matchesStatus(status, hasOpenHolding)) {
        continue;
      }
      rows.add(rowFor(product, holdings, hasOpenHolding, staleHoldingIds, asOf));
    }
    return rows.stream()
        .sorted(Comparator.comparing(r -> r.ticker() != null ? r.ticker() : r.name()))
        .toList();
  }

  private FiiPortfolioRow rowFor(
      InvestmentProduct product,
      List<InvestmentHolding> holdings,
      boolean hasOpenHolding,
      Set<UUID> staleHoldingIds,
      LocalDate asOf) {
    Set<UUID> holdingAccountIds =
        holdings.stream().map(InvestmentHolding::getAccountId).collect(Collectors.toSet());
    List<Transfer> trades = transferRepository.findByInvestmentProductId(product.getId());

    BigDecimal cotasHeld = BigDecimal.ZERO;
    BigDecimal amountContributed = BigDecimal.ZERO;
    for (Transfer trade : trades) {
      if (trade.getDate().isAfter(asOf)) {
        continue;
      }
      boolean buy = holdingAccountIds.contains(trade.getToAccountId());
      BigDecimal quantity = trade.getTradeDetails().quantity();
      if (quantity != null) {
        cotasHeld = buy ? cotasHeld.add(quantity) : cotasHeld.subtract(quantity);
      }
      amountContributed =
          buy
              ? amountContributed.add(trade.getAmount())
              : amountContributed.subtract(trade.getAmount());
    }

    BigDecimal currentValue = BigDecimal.ZERO;
    LocalDate latestSnapshotDate = null;
    boolean needsSnapshot = false;
    for (InvestmentHolding holding : holdings) {
      Optional<InvestmentSnapshot> latest = latestSnapshotQuery.latestOf(holding.getId(), asOf);
      if (latest.isPresent()) {
        currentValue = currentValue.add(latest.get().getBalance());
        LocalDate date = latest.get().getDate();
        if (latestSnapshotDate == null || date.isAfter(latestSnapshotDate)) {
          latestSnapshotDate = date;
        }
      }
      if (staleHoldingIds.contains(holding.getId())) {
        needsSnapshot = true;
      }
    }

    return new FiiPortfolioRow(
        product.getId(),
        product.getTicker(),
        product.getName(),
        product.getSegmentId(),
        cotasHeld,
        amountContributed,
        currentValue,
        latestSnapshotDate,
        needsSnapshot,
        hasOpenHolding);
  }

  private static boolean matchesStatus(InvestmentProductStatus status, boolean hasOpenHolding) {
    return switch (status) {
      case ALL -> true;
      case OPEN -> hasOpenHolding;
      case CLOSED -> !hasOpenHolding;
    };
  }

  /**
   * Every sub-category named exactly {@link AllocationPlanService#FII_SUBCATEGORY_NAME} (normally
   * exactly one, seeded by the migration under "Variable Income").
   */
  private Set<UUID> fiiSubcategoryIds() {
    Set<UUID> ids = new HashSet<>();
    for (InvestmentSubcategory subcategory : subcategoryRepository.findAll()) {
      if (AllocationPlanService.FII_SUBCATEGORY_NAME.equals(subcategory.getName())) {
        ids.add(subcategory.getId());
      }
    }
    return ids;
  }
}
