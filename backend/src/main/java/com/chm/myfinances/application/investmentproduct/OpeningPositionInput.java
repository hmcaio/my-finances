package com.chm.myfinances.application.investmentproduct;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The optional opening position of a product being created together with its first holding (F038
 * spec, ADR 0033): the same four values {@code InvestmentOpeningPositionService.upsert} takes
 * ({@code currentValue} may be {@code null}, meaning "record no snapshot").
 */
public record OpeningPositionInput(
    LocalDate asOfDate, BigDecimal quantity, BigDecimal unitPrice, BigDecimal currentValue) {}
