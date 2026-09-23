package com.chm.myfinances.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentTradeDetails} (F009 spec): record-only trade data,
 * every field optional, but {@code quantity} and {@code unitPrice} come together and are positive,
 * and {@code taxes} is non-negative.
 */
class InvestmentTradeDetailsTest {

  @Test
  void emptyHasNoFields() {
    InvestmentTradeDetails details = InvestmentTradeDetails.empty();

    assertThat(details.isEmpty()).isTrue();
    assertThat(details.quantity()).isNull();
    assertThat(details.unitPrice()).isNull();
    assertThat(details.taxes()).isNull();
  }

  @Test
  void keepsGivenFields() {
    InvestmentTradeDetails details =
        new InvestmentTradeDetails(
            new BigDecimal("0.12345678"),
            new BigDecimal("250000.00000001"),
            new BigDecimal("1.50"));

    assertThat(details.isEmpty()).isFalse();
    assertThat(details.quantity()).isEqualByComparingTo("0.12345678");
    assertThat(details.unitPrice()).isEqualByComparingTo("250000.00000001");
    assertThat(details.taxes()).isEqualByComparingTo("1.50");
  }

  @Test
  void allowsTaxesAlone() {
    InvestmentTradeDetails details = new InvestmentTradeDetails(null, null, BigDecimal.ZERO);

    assertThat(details.isEmpty()).isFalse();
    assertThat(details.taxes()).isEqualByComparingTo("0");
  }

  @Test
  void rejectsQuantityWithoutUnitPrice() {
    assertThatThrownBy(() -> new InvestmentTradeDetails(BigDecimal.ONE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnitPriceWithoutQuantity() {
    assertThatThrownBy(() -> new InvestmentTradeDetails(null, BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> new InvestmentTradeDetails(BigDecimal.ZERO, BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InvestmentTradeDetails(new BigDecimal("-1"), BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveUnitPrice() {
    assertThatThrownBy(() -> new InvestmentTradeDetails(BigDecimal.ONE, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InvestmentTradeDetails(BigDecimal.ONE, new BigDecimal("-1"), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeTaxes() {
    assertThatThrownBy(() -> new InvestmentTradeDetails(null, null, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
