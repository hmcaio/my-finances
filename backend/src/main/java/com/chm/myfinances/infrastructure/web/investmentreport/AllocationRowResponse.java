package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.AllocationRow;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One slice of {@code GET /api/investments/allocation} (F009 spec). With {@code groupBy=CATEGORY}
 * the sub-category fields are always {@code null}; with {@code SUBCATEGORY} they are {@code null}
 * only for the slice of products that have no sub-category. {@code needsSnapshot} is true when any
 * product in the slice has a trade newer than its latest snapshot.
 */
public record AllocationRowResponse(
    UUID categoryId,
    String categoryName,
    UUID subcategoryId,
    String subcategoryName,
    BigDecimal totalValue,
    boolean needsSnapshot) {

  public static AllocationRowResponse from(AllocationRow row) {
    return new AllocationRowResponse(
        row.categoryId(),
        row.categoryName(),
        row.subcategoryId(),
        row.subcategoryName(),
        row.totalValue(),
        row.needsSnapshot());
  }
}
