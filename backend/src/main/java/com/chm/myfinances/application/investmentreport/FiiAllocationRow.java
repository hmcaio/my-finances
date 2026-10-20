package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One slice of a FII allocation chart (F026 spec, ADR 0023): either a product (grouped by ticker)
 * or a segment, with its percentage of the FII-only total (not the whole portfolio). {@code key} is
 * the product id or segment id - {@code null} for the "No segment" bucket. {@code totalValue} is
 * the slice's current value (basis {@code ACTUAL}); {@code null} for basis {@code PLANNED}, which
 * has no value of its own, only a target percentage.
 *
 * <p>{@code segmentId} (Addendum - Nested Allocation Charts) is the row's own segment: populated on
 * a {@code TICKER}-groupBy row (the product's segment, {@code null} for an unsegmented product),
 * always {@code null} on a {@code SEGMENT}-groupBy row ({@code key} already carries that there). It
 * lets the frontend nest a {@code TICKER} chart's arcs under their parent {@code SEGMENT} chart's
 * arcs without a second lookup.
 */
public record FiiAllocationRow(
    UUID key, String label, BigDecimal totalValue, BigDecimal percentage, UUID segmentId) {}
