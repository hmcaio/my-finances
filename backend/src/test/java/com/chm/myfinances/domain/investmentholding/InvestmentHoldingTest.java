package com.chm.myfinances.domain.investmentholding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentHolding} (F022 spec, ADR 0020). Pure JUnit (ADR
 * 0004), written before the class itself.
 */
class InvestmentHoldingTest {

  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();

  private static InvestmentHolding newHolding() {
    return InvestmentHolding.create(UUID.randomUUID(), PRODUCT_ID, ACCOUNT_ID, null);
  }

  @Test
  void createsOpenWithGivenFields() {
    UUID id = UUID.randomUUID();

    InvestmentHolding holding =
        InvestmentHolding.create(id, PRODUCT_ID, ACCOUNT_ID, "Bought via XP promo");

    assertThat(holding.getId()).isEqualTo(id);
    assertThat(holding.getProductId()).isEqualTo(PRODUCT_ID);
    assertThat(holding.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(holding.getAdditionalNotes()).isEqualTo("Bought via XP promo");
    assertThat(holding.getClosedDate()).isNull();
    assertThat(holding.isClosed()).isFalse();
  }

  @Test
  void notesAreOptional() {
    InvestmentHolding holding = newHolding();

    assertThat(holding.getAdditionalNotes()).isNull();
  }

  @Test
  void createRejectsNullIdProductIdOrAccountId() {
    assertThatThrownBy(() -> InvestmentHolding.create(null, PRODUCT_ID, ACCOUNT_ID, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> InvestmentHolding.create(UUID.randomUUID(), null, ACCOUNT_ID, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> InvestmentHolding.create(UUID.randomUUID(), PRODUCT_ID, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void acceptsNotesAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1);

    assertThat(
            InvestmentHolding.create(UUID.randomUUID(), PRODUCT_ID, ACCOUNT_ID, atMax)
                .getAdditionalNotes())
        .isEqualTo(atMax);
    assertThatThrownBy(
            () -> InvestmentHolding.create(UUID.randomUUID(), PRODUCT_ID, ACCOUNT_ID, overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstitutePreservesClosedDateAndNotes() {
    LocalDate closed = LocalDate.of(2026, 5, 1);

    InvestmentHolding holding =
        InvestmentHolding.reconstitute(UUID.randomUUID(), PRODUCT_ID, ACCOUNT_ID, closed, "note");

    assertThat(holding.getClosedDate()).isEqualTo(closed);
    assertThat(holding.isClosed()).isTrue();
    assertThat(holding.getAdditionalNotes()).isEqualTo("note");
  }

  @Test
  void productIdAndAccountIdAreImmutable() {
    // No mutator exists for either field beyond construction; this documents the invariant that
    // re-pointing either would misattribute existing snapshots/trades (F022 spec).
    InvestmentHolding holding = newHolding();

    assertThat(holding.getProductId()).isEqualTo(PRODUCT_ID);
    assertThat(holding.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  void editNotesReplacesTheNotes() {
    InvestmentHolding holding = newHolding();

    holding.editNotes("Sold half in April");

    assertThat(holding.getAdditionalNotes()).isEqualTo("Sold half in April");
  }

  @Test
  void editNotesCanClearThem() {
    InvestmentHolding holding =
        InvestmentHolding.create(UUID.randomUUID(), PRODUCT_ID, ACCOUNT_ID, "note");

    holding.editNotes(null);

    assertThat(holding.getAdditionalNotes()).isNull();
  }

  @Test
  void editNotesRejectsOverMaxLengthAndKeepsTheOldValue() {
    InvestmentHolding holding =
        InvestmentHolding.create(UUID.randomUUID(), PRODUCT_ID, ACCOUNT_ID, "note");
    String overMax = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1);

    assertThatThrownBy(() -> holding.editNotes(overMax))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(holding.getAdditionalNotes()).isEqualTo("note");
  }

  @Test
  void closeSetsTheDateOnce() {
    InvestmentHolding holding = newHolding();

    LocalDate closedDate = LocalDate.now();
    holding.close(closedDate);

    assertThat(holding.isClosed()).isTrue();
    assertThat(holding.getClosedDate()).isEqualTo(closedDate);
  }

  @Test
  void closeThrowsWhenAlreadyClosed() {
    InvestmentHolding holding = newHolding();
    holding.close(LocalDate.now());

    assertThatThrownBy(() -> holding.close(LocalDate.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void closeRejectsANullDate() {
    InvestmentHolding holding = newHolding();

    assertThatThrownBy(() -> holding.close(null)).isInstanceOf(NullPointerException.class);
    assertThat(holding.isClosed()).isFalse();
  }
}
