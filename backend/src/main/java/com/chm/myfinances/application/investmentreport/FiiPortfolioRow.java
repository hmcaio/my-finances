package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the FII portfolio summary (F026 spec, ADR 0023): a product classified under the "REITs
 * (FIIs)" sub-category, aggregated across every one of its holdings/accounts.
 *
 * <p>{@code cotasHeld}/{@code amountContributed} are computed the same way {@code
 * InvestmentValueSeriesQuery} already derives {@code units}/{@code contributed} - a running total
 * over every tagged trade to date, not a monthly series point. {@code currentValue} is the sum of
 * every holding's latest snapshot; {@code latestSnapshotDate} is the most recent one across every
 * holding, or {@code null} if none has a snapshot yet. {@code needsSnapshot} is true when any
 * holding is stale. {@code hasOpenHolding} backs the status filter (at least one open holding).
 */
public record FiiPortfolioRow(
    UUID productId,
    String ticker,
    String name,
    UUID segmentId,
    BigDecimal cotasHeld,
    BigDecimal amountContributed,
    BigDecimal currentValue,
    LocalDate latestSnapshotDate,
    boolean needsSnapshot,
    boolean hasOpenHolding) {}
