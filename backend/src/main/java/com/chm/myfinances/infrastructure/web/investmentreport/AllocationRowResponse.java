package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.AllocationRow;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One slice of {@code GET /api/investments/allocation} (F009 spec, {@code groupBy=ACCOUNT} added by
 * F023). With {@code groupBy=CATEGORY} the sub-category fields are always {@code null}; with {@code
 * SUBCATEGORY} they are {@code null} only for the slice of products that have no sub-category; with
 * {@code ACCOUNT} the category/sub-category fields are always {@code null} and {@code
 * accountId}/{@code accountName} are populated instead. {@code needsSnapshot} is true when any
 * product (or, for {@code ACCOUNT}, any holding) in the slice has a trade newer than its latest
 * snapshot.
 */
public record AllocationRowResponse(
    UUID categoryId,
    String categoryName,
    UUID subcategoryId,
    String subcategoryName,
    UUID accountId,
    String accountName,
    BigDecimal totalValue,
    boolean needsSnapshot) {

  public static AllocationRowResponse from(AllocationRow row) {
    return new AllocationRowResponse(
        row.categoryId(),
        row.categoryName(),
        row.subcategoryId(),
        row.subcategoryName(),
        row.accountId(),
        row.accountName(),
        row.totalValue(),
        row.needsSnapshot());
  }
}
