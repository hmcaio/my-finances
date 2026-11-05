package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.EmergencyFundSuggestion;
import java.math.BigDecimal;

/**
 * API representation of an {@link EmergencyFundSuggestion} (F039 spec): {@code suggestedTarget =
 * months x averageMonthlyExpense}, the average taken over {@code monthsAveraged} full months. It
 * only fills a form field and is never stored.
 */
public record EmergencyFundSuggestionResponse(
    int months, BigDecimal averageMonthlyExpense, BigDecimal suggestedTarget, int monthsAveraged) {

  public static EmergencyFundSuggestionResponse from(EmergencyFundSuggestion suggestion) {
    return new EmergencyFundSuggestionResponse(
        suggestion.months(),
        suggestion.averageMonthlyExpense(),
        suggestion.suggestedTarget(),
        suggestion.monthsAveraged());
  }
}
