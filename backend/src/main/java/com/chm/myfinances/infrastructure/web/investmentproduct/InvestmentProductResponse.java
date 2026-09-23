package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of an {@link InvestmentProduct}. {@code hasHistory} (snapshots or buy/sell
 * transfers, F009) tells the client whether the product can still be hard-deleted or can only be
 * closed; it is on every response, list included, because the list is where the delete action
 * lives. {@code latestSnapshot} is the product's most recent snapshot ({@code null} if none) and
 * {@code needsSnapshot} whether a trade is newer than it as of today (F009) - both computed on
 * read, never stored.
 */
public record InvestmentProductResponse(
    UUID id,
    UUID accountId,
    UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    String name,
    LocalDate closedDate,
    boolean closed,
    boolean hasHistory,
    boolean needsSnapshot,
    LatestSnapshotResponse latestSnapshot) {

  /** The date and value of a product's most recent snapshot. */
  public record LatestSnapshotResponse(LocalDate date, BigDecimal balance) {

    static LatestSnapshotResponse from(InvestmentSnapshot snapshot) {
      return snapshot == null
          ? null
          : new LatestSnapshotResponse(snapshot.getDate(), snapshot.getBalance());
    }
  }

  public static InvestmentProductResponse from(
      InvestmentProduct product,
      boolean hasHistory,
      boolean needsSnapshot,
      InvestmentSnapshot latestSnapshot) {
    return new InvestmentProductResponse(
        product.getId(),
        product.getAccountId(),
        product.getInvestmentCategoryId(),
        product.getInvestmentSubcategoryId(),
        product.getName(),
        product.getClosedDate(),
        product.isClosed(),
        hasHistory,
        needsSnapshot,
        LatestSnapshotResponse.from(latestSnapshot));
  }
}
