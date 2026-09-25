package com.chm.myfinances.domain.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentSnapshot} (PRD S5.8, F009 spec). Pure JUnit, written
 * before the class itself (ADR 0004).
 */
class InvestmentSnapshotTest {

  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final LocalDate DATE = LocalDate.of(2026, 3, 31);

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();

    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(id, PRODUCT_ID, DATE, new BigDecimal("1234.56"));

    assertThat(snapshot.getId()).isEqualTo(id);
    assertThat(snapshot.getProductId()).isEqualTo(PRODUCT_ID);
    assertThat(snapshot.getDate()).isEqualTo(DATE);
    assertThat(snapshot.getBalance()).isEqualByComparingTo("1234.56");
  }

  @Test
  void createAllowsAZeroBalanceForALiquidatedPosition() {
    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, BigDecimal.ZERO);

    assertThat(snapshot.getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void createRejectsANegativeBalance() {
    assertThatThrownBy(
            () ->
                InvestmentSnapshot.create(
                    UUID.randomUUID(), PRODUCT_ID, DATE, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullFields() {
    assertThatThrownBy(() -> InvestmentSnapshot.create(null, PRODUCT_ID, DATE, BigDecimal.ONE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentSnapshot.create(UUID.randomUUID(), null, DATE, BigDecimal.ONE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, null, BigDecimal.ONE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void replaceBalanceUpdatesTheBalance() {
    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, BigDecimal.TEN);

    snapshot.replaceBalance(new BigDecimal("99.99"));

    assertThat(snapshot.getBalance()).isEqualByComparingTo("99.99");
  }

  @Test
  void replaceBalanceAllowsZero() {
    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, BigDecimal.TEN);

    snapshot.replaceBalance(BigDecimal.ZERO);

    assertThat(snapshot.getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void replaceBalanceRejectsANegativeBalanceAndKeepsTheOldOne() {
    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, BigDecimal.TEN);

    assertThatThrownBy(() -> snapshot.replaceBalance(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot.getBalance()).isEqualByComparingTo("10");
  }

  @Test
  void moveToChangesTheDate() {
    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, BigDecimal.TEN);

    snapshot.moveTo(DATE.minusDays(3));

    assertThat(snapshot.getDate()).isEqualTo(DATE.minusDays(3));
  }

  @Test
  void moveToRejectsANullDateAndKeepsTheOldOne() {
    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(UUID.randomUUID(), PRODUCT_ID, DATE, BigDecimal.TEN);

    assertThatThrownBy(() -> snapshot.moveTo(null)).isInstanceOf(NullPointerException.class);
    assertThat(snapshot.getDate()).isEqualTo(DATE);
  }

  @Test
  void reconstituteRebuildsFromPersistedState() {
    UUID id = UUID.randomUUID();

    InvestmentSnapshot snapshot =
        InvestmentSnapshot.reconstitute(id, PRODUCT_ID, DATE, new BigDecimal("5.00"));

    assertThat(snapshot.getId()).isEqualTo(id);
    assertThat(snapshot.getProductId()).isEqualTo(PRODUCT_ID);
    assertThat(snapshot.getDate()).isEqualTo(DATE);
    assertThat(snapshot.getBalance()).isEqualByComparingTo("5.00");
  }
}
