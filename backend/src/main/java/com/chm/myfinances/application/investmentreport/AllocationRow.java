package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One slice of the allocation view (F009 spec). For {@link AllocationGrouping#CATEGORY} the
 * sub-category fields are always {@code null}; for {@code SUBCATEGORY} they are {@code null} only
 * for the slice of products that have no sub-category. {@code needsSnapshot} is true when any
 * product in the group is stale.
 */
public record AllocationRow(
    UUID categoryId,
    String categoryName,
    UUID subcategoryId,
    String subcategoryName,
    BigDecimal totalValue,
    boolean needsSnapshot) {}
