package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One slice of a FII allocation chart (F026 spec, ADR 0023): either a product (grouped by ticker)
 * or a segment, with its percentage of the FII-only total (not the whole portfolio). {@code key} is
 * the product id or segment id - {@code null} for the "No segment" bucket. {@code totalValue} is
 * the slice's current value (basis {@code ACTUAL}); {@code null} for basis {@code PLANNED}, which
 * has no value of its own, only a target percentage.
 */
public record FiiAllocationRow(
    UUID key, String label, BigDecimal totalValue, BigDecimal percentage) {}
