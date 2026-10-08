package com.chm.myfinances.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link TradeConfirmation} (F027 spec, ADR 0024). Pure JUnit - no
 * Spring context, no database (ADR 0004) - written before {@link TradeConfirmation} itself, per
 * F027's plan.md.
 */
class TradeConfirmationTest {

  private static final UUID PRODUCT_A = UUID.randomUUID();
  private static final UUID PRODUCT_B = UUID.randomUUID();

  private static TradeConfirmationLine line(
      UUID productId, TradeSide side, String quantity, String unitPrice) {
    return new TradeConfirmationLine(
        productId, side, new BigDecimal(quantity), new BigDecimal(unitPrice), null, false);
  }

  @Test
  void rejectsNullLines() {
    assertThatThrownBy(() -> TradeConfirmation.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsEmptyLines() {
    assertThatThrownBy(() -> TradeConfirmation.of(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsASingleLine() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(line(PRODUCT_A, TradeSide.BUY, "10", "100.00")));

    assertThat(confirmation.getLines()).hasSize(1);
  }

  @Test
  void acceptsMixedBuyAndSellLinesIncludingTheSameProductOnBothSides() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                line(PRODUCT_A, TradeSide.BUY, "10", "100.00"),
                line(PRODUCT_B, TradeSide.SELL, "5", "50.00"),
                line(PRODUCT_A, TradeSide.SELL, "3", "110.00")));

    assertThat(confirmation.getLines()).hasSize(3);
  }

  @Test
  void acceptsTheSameProductOnMoreThanOneLineAsPartialFills() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                line(PRODUCT_A, TradeSide.BUY, "10", "100.00"),
                line(PRODUCT_A, TradeSide.BUY, "5", "101.00")));

    assertThat(confirmation.getLines()).hasSize(2);
  }

  @Test
  void rejectsTwoLinesOfTheSameProductBothCarryingAResultingBalance() {
    TradeConfirmationLine first =
        new TradeConfirmationLine(
            PRODUCT_A, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("10"), false);
    TradeConfirmationLine second =
        new TradeConfirmationLine(
            PRODUCT_A, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("20"), false);

    assertThatThrownBy(() -> TradeConfirmation.of(List.of(first, second)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsResultingBalanceOnOneLinePerProductAcrossDifferentProducts() {
    TradeConfirmationLine a =
        new TradeConfirmationLine(
            PRODUCT_A, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("10"), false);
    TradeConfirmationLine b =
        new TradeConfirmationLine(
            PRODUCT_B, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("20"), false);

    TradeConfirmation confirmation = TradeConfirmation.of(List.of(a, b));

    assertThat(confirmation.getLines()).hasSize(2);
  }

  // --- netCost ----------------------------------------------------------------------------------

  @Test
  void netCostOfASingleBuySumsTotalPlusTaxes() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(line(PRODUCT_A, TradeSide.BUY, "10", "100.00")));

    assertThat(confirmation.netCost(new BigDecimal("5.00"))).isEqualByComparingTo("1005.00");
  }

  @Test
  void netCostOfASingleSellSubtractsTotalThenAddsTaxes() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(line(PRODUCT_A, TradeSide.SELL, "10", "100.00")));

    // -1000 + 5 = -995: net proceeds of 995, after a 5.00 fee.
    assertThat(confirmation.netCost(new BigDecimal("5.00"))).isEqualByComparingTo("-995.00");
  }

  @Test
  void netCostNetsBuyAgainstSellAcrossMultipleLines() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                line(PRODUCT_A, TradeSide.BUY, "10", "100.00"), // +1000
                line(PRODUCT_B, TradeSide.SELL, "5", "50.00"))); // -250

    assertThat(confirmation.netCost(new BigDecimal("10.00"))).isEqualByComparingTo("760.00");
  }

  @Test
  void netCostSumsPartialFillsOfTheSameProduct() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                line(PRODUCT_A, TradeSide.BUY, "10", "100.00"), // +1000
                line(PRODUCT_A, TradeSide.BUY, "5", "101.00"))); // +505

    assertThat(confirmation.netCost(BigDecimal.ZERO)).isEqualByComparingTo("1505.00");
  }

  @Test
  void netCostRoundsOnlyTheFinalFigureHalfUp() {
    // 0.333 * 3 = 0.999, +0.0006 taxes => 0.9996 -> rounds to 1.00 (HALF_UP), not per-line
    // rounding.
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(line(PRODUCT_A, TradeSide.BUY, "3", "0.333")));

    assertThat(confirmation.netCost(new BigDecimal("0.0006"))).isEqualByComparingTo("1.00");
  }

  @Test
  void netCostRejectsAnExactZeroResult() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                line(PRODUCT_A, TradeSide.BUY, "10", "100.00"), // +1000
                line(PRODUCT_B, TradeSide.SELL, "10", "100.00"))); // -1000

    assertThatThrownBy(() -> confirmation.netCost(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void netCostRejectsNullTaxes() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(line(PRODUCT_A, TradeSide.BUY, "10", "100.00")));

    assertThatThrownBy(() -> confirmation.netCost(null)).isInstanceOf(NullPointerException.class);
  }
}
