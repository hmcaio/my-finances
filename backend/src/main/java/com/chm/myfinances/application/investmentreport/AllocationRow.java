package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One slice of the allocation view (F009 spec, {@code ACCOUNT} grouping added by F023, {@code
 * INSTITUTION} added by F030). For {@link AllocationGrouping#CATEGORY} the sub-category fields are
 * always {@code null}; for {@code SUBCATEGORY} they are {@code null} only for the slice of products
 * that have no sub-category; for {@code ACCOUNT} the category/sub-category fields are always {@code
 * null} and {@code accountId}/{@code accountName} are populated instead; for {@code INSTITUTION}
 * only {@code institutionId}/{@code institutionName} are populated. {@code needsSnapshot} is true
 * when any product (or, for {@code ACCOUNT}/{@code INSTITUTION}, any holding) in the group is
 * stale.
 */
public record AllocationRow(
    UUID categoryId,
    String categoryName,
    UUID subcategoryId,
    String subcategoryName,
    UUID accountId,
    String accountName,
    UUID institutionId,
    String institutionName,
    BigDecimal totalValue,
    boolean needsSnapshot) {}
