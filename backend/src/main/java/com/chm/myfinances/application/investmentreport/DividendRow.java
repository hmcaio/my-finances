package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One dividend {@code Transaction} (F026 spec, ADR 0023), with its ticker joined in via its
 * {@code investmentHoldingId} -&gt; product lookup, for the dividend history view.
 */
public record DividendRow(
    UUID transactionId,
    LocalDate date,
    BigDecimal amount,
    UUID investmentHoldingId,
    UUID productId,
    String ticker,
    String productName,
    String description) {}
