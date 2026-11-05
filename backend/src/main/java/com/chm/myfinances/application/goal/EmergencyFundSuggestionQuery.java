package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.transaction.CategorySpend;
import com.chm.myfinances.application.transaction.MonthlySpendByCategoryQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The emergency-fund target helper (F039 spec, PRD S6.17): {@code months x} the average monthly
 * expense of the last 12 <em>full</em> months before the current one. Months before the first
 * expense are skipped, so a new user with three months of data does not get an average divided by
 * twelve; a gap after the first expense counts as a zero month. Built from {@link
 * MonthlySpendByCategoryQuery#forMonth}, which sums expense transactions only: transfers (card
 * payments, trades) are not expenses (PRD S5.5). The suggestion fills a form field and is never
 * stored.
 */
@Service
public class EmergencyFundSuggestionQuery {

  static final int MIN_MONTHS = 1;
  static final int MAX_MONTHS = 120;

  /** How many full months before the current one the average looks back over. */
  private static final int WINDOW_MONTHS = 12;

  private final MonthlySpendByCategoryQuery spendQuery;
  private final Clock clock;

  public EmergencyFundSuggestionQuery(MonthlySpendByCategoryQuery spendQuery, Clock clock) {
    this.spendQuery = spendQuery;
    this.clock = clock;
  }

  /** 400 {@link InvalidGoalException} for {@code months} outside {@code 1..120}. */
  public EmergencyFundSuggestion suggest(int months) {
    if (months < MIN_MONTHS || months > MAX_MONTHS) {
      throw new InvalidGoalException("months must be between " + MIN_MONTHS + " and " + MAX_MONTHS);
    }
    YearMonth current = YearMonth.now(clock);
    List<BigDecimal> totals = new ArrayList<>();
    boolean started = false;
    for (int back = WINDOW_MONTHS; back >= 1; back--) {
      BigDecimal total = totalOf(current.minusMonths(back));
      started = started || total.signum() > 0;
      if (started) {
        totals.add(total);
      }
    }
    if (totals.isEmpty()) {
      return new EmergencyFundSuggestion(
          months, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0);
    }
    BigDecimal average =
        totals.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(totals.size()), 2, RoundingMode.HALF_UP);
    return new EmergencyFundSuggestion(
        months, average, average.multiply(BigDecimal.valueOf(months)), totals.size());
  }

  private BigDecimal totalOf(YearMonth month) {
    return spendQuery.forMonth(month).stream()
        .map(CategorySpend::total)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
