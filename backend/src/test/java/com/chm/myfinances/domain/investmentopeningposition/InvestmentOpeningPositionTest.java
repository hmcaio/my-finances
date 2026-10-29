package com.chm.myfinances.domain.investmentopeningposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentOpeningPosition} (F038, ADR 0033). Pure JUnit,
 * written before the class itself (ADR 0004).
 */
class InvestmentOpeningPositionTest {

  private static final UUID HOLDING_ID = UUID.randomUUID();
  private static final LocalDate AS_OF = LocalDate.of(2026, 10, 1);

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();

    InvestmentOpeningPosition position =
        InvestmentOpeningPosition.create(
            id, HOLDING_ID, AS_OF, new BigDecimal("40"), new BigDecimal("150.00"));

    assertThat(position.getId()).isEqualTo(id);
    assertThat(position.getHoldingId()).isEqualTo(HOLDING_ID);
    assertThat(position.getAsOfDate()).isEqualTo(AS_OF);
    assertThat(position.getQuantity()).isEqualByComparingTo("40");
    assertThat(position.getUnitPrice()).isEqualByComparingTo("150.00");
  }

  @Test
  void createRejectsNullFields() {
    BigDecimal one = BigDecimal.ONE;
    assertThatThrownBy(() -> InvestmentOpeningPosition.create(null, HOLDING_ID, AS_OF, one, one))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentOpeningPosition.create(UUID.randomUUID(), null, AS_OF, one, one))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentOpeningPosition.create(UUID.randomUUID(), HOLDING_ID, null, one, one))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> InvestmentOpeningPosition.create(UUID.randomUUID(), HOLDING_ID, AS_OF, null, one))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> InvestmentOpeningPosition.create(UUID.randomUUID(), HOLDING_ID, AS_OF, one, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsANonPositiveQuantity() {
    assertThatThrownBy(
            () ->
                InvestmentOpeningPosition.create(
                    UUID.randomUUID(), HOLDING_ID, AS_OF, BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                InvestmentOpeningPosition.create(
                    UUID.randomUUID(), HOLDING_ID, AS_OF, new BigDecimal("-1"), BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsANonPositiveUnitPrice() {
    assertThatThrownBy(
            () ->
                InvestmentOpeningPosition.create(
                    UUID.randomUUID(), HOLDING_ID, AS_OF, BigDecimal.ONE, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                InvestmentOpeningPosition.create(
                    UUID.randomUUID(), HOLDING_ID, AS_OF, BigDecimal.ONE, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void amountContributedIsQuantityTimesUnitPriceUnrounded() {
    InvestmentOpeningPosition position =
        InvestmentOpeningPosition.create(
            UUID.randomUUID(),
            HOLDING_ID,
            AS_OF,
            new BigDecimal("3.33333333"),
            new BigDecimal("1.23456789"));

    assertThat(position.amountContributed()).isEqualByComparingTo("4.1152262958847737");
    assertThat(position.amountContributed())
        .isEqualTo(new BigDecimal("3.33333333").multiply(new BigDecimal("1.23456789")));
  }

  @Test
  void editReplacesTheThreeValuesAndKeepsTheHolding() {
    UUID id = UUID.randomUUID();
    InvestmentOpeningPosition position =
        InvestmentOpeningPosition.create(
            id, HOLDING_ID, AS_OF, new BigDecimal("40"), new BigDecimal("150.00"));

    position.edit(LocalDate.of(2026, 9, 1), new BigDecimal("50"), new BigDecimal("140.00"));

    assertThat(position.getId()).isEqualTo(id);
    assertThat(position.getHoldingId()).isEqualTo(HOLDING_ID);
    assertThat(position.getAsOfDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(position.getQuantity()).isEqualByComparingTo("50");
    assertThat(position.getUnitPrice()).isEqualByComparingTo("140.00");
  }

  @Test
  void editRejectsInvalidValuesAndLeavesThePositionUnchanged() {
    InvestmentOpeningPosition position =
        InvestmentOpeningPosition.create(
            UUID.randomUUID(), HOLDING_ID, AS_OF, new BigDecimal("40"), new BigDecimal("150"));

    assertThatThrownBy(() -> position.edit(AS_OF, BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> position.edit(null, BigDecimal.ONE, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(position.getQuantity()).isEqualByComparingTo("40");
    assertThat(position.getAsOfDate()).isEqualTo(AS_OF);
  }

  @Test
  void auditSnapshotCarriesHoldingDateQuantityAndUnitPrice() {
    InvestmentOpeningPosition position =
        InvestmentOpeningPosition.create(
            UUID.randomUUID(), HOLDING_ID, AS_OF, new BigDecimal("40"), new BigDecimal("150.00"));

    assertThat(position.toAuditSnapshot())
        .containsExactly(
            entry("holdingId", HOLDING_ID.toString()),
            entry("asOfDate", "2026-10-01"),
            entry("quantity", new BigDecimal("40")),
            entry("unitPrice", new BigDecimal("150.00")));
  }
}
