package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pace functions of {@link GoalProjectionMath} (F041 spec, ADR 0035), written
 * first (ADR 0004): the average monthly increase of a goal's value over its history window, and the
 * months a straight line at that pace needs to reach a target.
 */
class GoalProjectionMathTest {

  private static List<BigDecimal> values(String... amounts) {
    return Arrays.stream(amounts).map(BigDecimal::new).toList();
  }

  // --- monthlyPace ---

  @Test
  void aRisingSeriesPacesAtLastMinusFirstOverTheNumberOfIntervals() {
    // 4 points = 3 intervals: (1300 - 1000) / 3.
    assertThat(GoalProjectionMath.monthlyPace(values("1000", "1050", "1200", "1300")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("100.00"));
  }

  @Test
  void theIncreaseIsRoundedToTheCentHalfUp() {
    // (1000.10 - 1000.00) / 3 = 0.03333... ; (1000.05 - 1000.00) / 3 = 0.016666...
    assertThat(GoalProjectionMath.monthlyPace(values("1000.00", "0", "0", "1000.10")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("0.03"));
    assertThat(GoalProjectionMath.monthlyPace(values("0.00", "0", "0", "0.05")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("0.02"));
    assertThat(GoalProjectionMath.monthlyPace(values("0.00", "0", "0", "0.045")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("0.02"));
  }

  @Test
  void aFlatSeriesIsZero() {
    assertThat(GoalProjectionMath.monthlyPace(values("500", "500", "500", "500")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("0.00"));
  }

  @Test
  void aFallingSeriesIsNegative() {
    assertThat(GoalProjectionMath.monthlyPace(values("1000", "900", "800", "700")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("-100.00"));
  }

  @Test
  void onlyTheFirstAndLastValuesMatter() {
    assertThat(GoalProjectionMath.monthlyPace(values("0", "5000", "-3000", "300", "600")))
        .hasValueSatisfying(pace -> assertThat(pace).isEqualByComparingTo("150.00"));
  }

  @Test
  void fewerThanFourValuesHaveNoPace() {
    assertThat(GoalProjectionMath.monthlyPace(values())).isEmpty();
    assertThat(GoalProjectionMath.monthlyPace(values("100"))).isEmpty();
    assertThat(GoalProjectionMath.monthlyPace(values("100", "200", "300"))).isEmpty();
  }

  // --- paceMonthsToTarget ---

  @Test
  void aTargetAlreadyReachedIsZeroMonthsEvenAtAZeroOrNegativePace() {
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("1000"), bd("0"), bd("1000"))).isZero();
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("1200"), bd("-50"), bd("1000"))).isZero();
  }

  @Test
  void aZeroOrNegativePaceNeverReachesTheTarget() {
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("100"), bd("0"), bd("1000"))).isNull();
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("100"), bd("-10"), bd("1000"))).isNull();
  }

  @Test
  void theMonthsAreTheCeilingOfTheGapOverThePace() {
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("100"), bd("100"), bd("1000")))
        .isEqualTo(9);
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("100"), bd("100"), bd("1001")))
        .isEqualTo(10);
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("0"), bd("0.01"), bd("0.01"))).isEqualTo(1);
  }

  @Test
  void beyondSixHundredMonthsIsNotReached() {
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("0"), bd("10"), bd("6000"))).isEqualTo(600);
    assertThat(GoalProjectionMath.paceMonthsToTarget(bd("0"), bd("10"), bd("6000.01"))).isNull();
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
