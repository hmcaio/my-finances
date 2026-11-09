package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.ContributionSuggestion;
import java.math.BigDecimal;

/**
 * API representation of a {@link ContributionSuggestion} (F041 spec, ADR 0035): the average monthly
 * amount put into the goal's products over the {@code monthsAveraged} full months that count. It
 * only fills a form field and is never stored.
 */
public record ContributionSuggestionResponse(
    BigDecimal averageMonthlyContribution, int monthsAveraged) {

  public static ContributionSuggestionResponse from(ContributionSuggestion suggestion) {
    return new ContributionSuggestionResponse(
        suggestion.averageMonthlyContribution(), suggestion.monthsAveraged());
  }
}
