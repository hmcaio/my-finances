package com.chm.myfinances.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link TradeConfirmationLine} (F027 spec, ADR 0024). Pure JUnit - no
 * Spring context, no database (ADR 0004) - written before {@link TradeConfirmationLine} itself, per
 * F027's plan.md.
 */
class TradeConfirmationLineTest {

  private static final UUID PRODUCT_ID = UUID.randomUUID();

  @Test
  void keepsGivenFields() {
    TradeConfirmationLine line =
        new TradeConfirmationLine(
            PRODUCT_ID,
            TradeSide.BUY,
            new BigDecimal("10"),
            new BigDecimal("100.00"),
            new BigDecimal("1000.00"),
            false);

    assertThat(line.productId()).isEqualTo(PRODUCT_ID);
    assertThat(line.side()).isEqualTo(TradeSide.BUY);
    assertThat(line.quantity()).isEqualByComparingTo("10");
    assertThat(line.unitPrice()).isEqualByComparingTo("100.00");
    assertThat(line.resultingBalance()).isEqualByComparingTo("1000.00");
    assertThat(line.closeHolding()).isFalse();
  }

  @Test
  void resultingBalanceAndCloseHoldingAreOptional() {
    TradeConfirmationLine line =
        new TradeConfirmationLine(
            PRODUCT_ID, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, null, false);

    assertThat(line.resultingBalance()).isNull();
    assertThat(line.closeHolding()).isFalse();
  }

  @Test
  void totalIsQuantityTimesUnitPrice() {
    TradeConfirmationLine line =
        new TradeConfirmationLine(
            PRODUCT_ID, TradeSide.BUY, new BigDecimal("10"), new BigDecimal("100.00"), null, false);

    assertThat(line.total()).isEqualByComparingTo("1000.00");
  }

  @Test
  void rejectsNullProductId() {
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    null, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, null, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullSide() {
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, null, BigDecimal.ONE, BigDecimal.TEN, null, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullOrNonPositiveQuantity() {
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, TradeSide.BUY, null, BigDecimal.TEN, null, false))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, TradeSide.BUY, BigDecimal.ZERO, BigDecimal.TEN, null, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, TradeSide.BUY, new BigDecimal("-1"), BigDecimal.TEN, null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullOrNonPositiveUnitPrice() {
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, TradeSide.BUY, BigDecimal.ONE, null, null, false))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, TradeSide.BUY, BigDecimal.ONE, BigDecimal.ZERO, null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeResultingBalance() {
    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID,
                    TradeSide.SELL,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    new BigDecimal("-0.01"),
                    false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsZeroResultingBalance() {
    TradeConfirmationLine line =
        new TradeConfirmationLine(
            PRODUCT_ID, TradeSide.SELL, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, true);

    assertThat(line.resultingBalance()).isEqualByComparingTo("0");
  }

  @Test
  void closeHoldingIsAllowedOnlyOnASellLine() {
    TradeConfirmationLine sellClose =
        new TradeConfirmationLine(
            PRODUCT_ID, TradeSide.SELL, BigDecimal.ONE, BigDecimal.TEN, null, true);
    assertThat(sellClose.closeHolding()).isTrue();

    assertThatThrownBy(
            () ->
                new TradeConfirmationLine(
                    PRODUCT_ID, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closeHolding");
  }
}
