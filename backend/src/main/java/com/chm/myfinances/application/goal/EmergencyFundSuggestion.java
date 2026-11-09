package com.chm.myfinances.application.goal;

import java.math.BigDecimal;

/**
 * An emergency-fund target suggestion (F039 spec): {@code suggestedTarget = months x
 * averageMonthlyExpense}, where the average runs over the {@code monthsAveraged} full months that
 * count (up to the last 12 before the current one; months before the first expense are skipped).
 * With no expense at all, the average and the target are {@code 0} and {@code monthsAveraged} is
 * {@code 0}.
 */
public record EmergencyFundSuggestion(
    int months, BigDecimal averageMonthlyExpense, BigDecimal suggestedTarget, int monthsAveraged) {}
