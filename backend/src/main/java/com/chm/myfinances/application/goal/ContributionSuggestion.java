package com.chm.myfinances.application.goal;

import java.math.BigDecimal;

/**
 * A planned-contribution suggestion from history (F041 spec, ADR 0035): {@code
 * averageMonthlyContribution} is the average, over the {@code monthsAveraged} full months that
 * count, of what was actually put into the goal's allocated products (the goal's share of each
 * product's buys minus sells, opening-position costs removed), floored at zero. With no allocation
 * or no contribution at all, both are {@code 0}. It fills a form field and is never stored.
 */
public record ContributionSuggestion(BigDecimal averageMonthlyContribution, int monthsAveraged) {}
