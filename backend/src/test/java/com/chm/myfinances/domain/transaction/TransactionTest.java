package com.chm.myfinances.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Transaction} (PRD S5.3, F004 spec). Pure JUnit - no Spring
 * context, no database (ADR 0004) - written before {@link Transaction} itself, per F004's plan.md.
 */
class TransactionTest {

  private static final UUID CATEGORY_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final UUID PAYMENT_METHOD_ID = UUID.randomUUID();

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 3, 15);

    Transaction transaction =
        Transaction.create(
            id,
            date,
            new BigDecimal("42.50"),
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Weekly groceries",
            "Bought extra for the weekend");

    assertThat(transaction.getId()).isEqualTo(id);
    assertThat(transaction.getDate()).isEqualTo(date);
    assertThat(transaction.getAmount()).isEqualByComparingTo("42.50");
    assertThat(transaction.getCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(transaction.getType()).isEqualTo(CategoryType.EXPENSE);
    assertThat(transaction.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(transaction.getPaymentMethodId()).isEqualTo(PAYMENT_METHOD_ID);
    assertThat(transaction.getRecurringTemplateVersionId()).isNull();
    assertThat(transaction.getDescription()).isEqualTo("Weekly groceries");
    assertThat(transaction.getAdditionalNotes()).isEqualTo("Bought extra for the weekend");
  }

  @Test
  void createAllowsNullAdditionalNotes() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.ONE,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Salary",
            null);

    assertThat(transaction.getAdditionalNotes()).isNull();
  }

  @Test
  void createRejectsNullDescription() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.ONE,
                    CATEGORY_ID,
                    CategoryType.INCOME,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsBlankDescription() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.ONE,
                    CATEGORY_ID,
                    CategoryType.INCOME,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "   ",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsDescriptionExceedingMaxLength() {
    String tooLong = "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1);

    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.ONE,
                    CATEGORY_ID,
                    CategoryType.INCOME,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    tooLong,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAdditionalNotesExceedingMaxLength() {
    String tooLong = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1);

    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.ONE,
                    CATEGORY_ID,
                    CategoryType.INCOME,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Salary",
                    tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAllowsARecurringTemplateVersionId() {
    UUID recurringTemplateVersionId = UUID.randomUUID();

    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            recurringTemplateVersionId,
            "Groceries",
            null);

    assertThat(transaction.getRecurringTemplateVersionId()).isEqualTo(recurringTemplateVersionId);
  }

  @Test
  void typeIsCapturedAtCreationIndependentlyOfCategoryId() {
    // Belt-and-suspenders per F004 spec: type is denormalized from the category's type at
    // creation time rather than re-derived every read, so it's just a plain stored field here -
    // this test documents that the value passed in at construction is exactly what's exposed.
    Transaction incomeTransaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Salary",
            null);

    assertThat(incomeTransaction.getType()).isEqualTo(CategoryType.INCOME);
  }

  @Test
  void createRejectsNullDate() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    null,
                    BigDecimal.TEN,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullAmount() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    null,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsZeroAmount() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.ZERO,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNegativeAmount() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    new BigDecimal("-10.00"),
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullCategoryId() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    null,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullType() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    CATEGORY_ID,
                    null,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullAccountId() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    null,
                    PAYMENT_METHOD_ID,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullPaymentMethodId() {
    assertThatThrownBy(
            () ->
                Transaction.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    null,
                    null,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void editUpdatesEveryEditableField() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Original description",
            "Original note");

    UUID newCategoryId = UUID.randomUUID();
    UUID newAccountId = UUID.randomUUID();
    UUID newPaymentMethodId = UUID.randomUUID();
    transaction.edit(
        LocalDate.of(2026, 2, 2),
        new BigDecimal("99.99"),
        newCategoryId,
        CategoryType.INCOME,
        newAccountId,
        newPaymentMethodId,
        "Updated description",
        "Updated note");

    assertThat(transaction.getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(transaction.getAmount()).isEqualByComparingTo("99.99");
    assertThat(transaction.getCategoryId()).isEqualTo(newCategoryId);
    assertThat(transaction.getType()).isEqualTo(CategoryType.INCOME);
    assertThat(transaction.getAccountId()).isEqualTo(newAccountId);
    assertThat(transaction.getPaymentMethodId()).isEqualTo(newPaymentMethodId);
    assertThat(transaction.getDescription()).isEqualTo("Updated description");
    assertThat(transaction.getAdditionalNotes()).isEqualTo("Updated note");
  }

  @Test
  void editDoesNotTouchIdOrRecurringTemplateVersionId() {
    UUID id = UUID.randomUUID();
    UUID recurringTemplateVersionId = UUID.randomUUID();
    Transaction transaction =
        Transaction.create(
            id,
            LocalDate.of(2026, 1, 1),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            recurringTemplateVersionId,
            "Groceries",
            null);

    transaction.edit(
        LocalDate.of(2026, 3, 3),
        BigDecimal.ONE,
        UUID.randomUUID(),
        CategoryType.INCOME,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Hand-edited, not template-edited (PRD S5.7)",
        null);

    assertThat(transaction.getId()).isEqualTo(id);
    assertThat(transaction.getRecurringTemplateVersionId()).isEqualTo(recurringTemplateVersionId);
  }

  @Test
  void editRejectsZeroOrNegativeAmount() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Groceries",
            null);

    assertThatThrownBy(
            () ->
                transaction.edit(
                    LocalDate.now(),
                    BigDecimal.ZERO,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    "Groceries",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsNullDate() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Groceries",
            null);

    assertThatThrownBy(
            () ->
                transaction.edit(
                    null,
                    BigDecimal.TEN,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    "Groceries",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void editRejectsBlankDescription() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Groceries",
            null);

    assertThatThrownBy(
            () ->
                transaction.edit(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    CATEGORY_ID,
                    CategoryType.EXPENSE,
                    ACCOUNT_ID,
                    PAYMENT_METHOD_ID,
                    "   ",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // F024 (ADR 0021): Transaction gains optional fuelDetails, carried through create/edit
  // unchanged otherwise - no cross-check here against the category being the fuel category
  // (an application-layer concern, since this domain package never imports domain.category
  // beyond CategoryType, and never imports domain.vehicle at all).

  @Test
  void createWithoutFuelDetailsLeavesItNull() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Groceries",
            null);

    assertThat(transaction.getFuelDetails()).isNull();
  }

  @Test
  void createWithFuelDetailsCarriesThemThrough() {
    FuelDetails fuelDetails =
        new FuelDetails(
            UUID.randomUUID(),
            FuelType.GASOLINA,
            new BigDecimal("40.500"),
            new BigDecimal("5.799"),
            null,
            null);

    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Fuel",
            null,
            fuelDetails);

    assertThat(transaction.getFuelDetails()).isEqualTo(fuelDetails);
    // Every other field is exactly as it would be without fuel details.
    assertThat(transaction.getDescription()).isEqualTo("Fuel");
    assertThat(transaction.getAmount()).isEqualByComparingTo(BigDecimal.TEN);
  }

  @Test
  void editWithoutFuelDetailsClearsAnyPreviouslyRecordedOnes() {
    FuelDetails fuelDetails =
        new FuelDetails(
            UUID.randomUUID(), FuelType.ETANOL, BigDecimal.TEN, BigDecimal.ONE, null, null);
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Fuel",
            null,
            fuelDetails);

    transaction.edit(
        LocalDate.now(),
        BigDecimal.TEN,
        CATEGORY_ID,
        CategoryType.EXPENSE,
        ACCOUNT_ID,
        PAYMENT_METHOD_ID,
        "Groceries",
        null);

    assertThat(transaction.getFuelDetails()).isNull();
  }

  @Test
  void editWithFuelDetailsReplacesAnyPreviousOnes() {
    UUID vehicleId = UUID.randomUUID();
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.EXPENSE,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Groceries",
            null);
    FuelDetails fuelDetails =
        new FuelDetails(vehicleId, FuelType.ETANOL, BigDecimal.TEN, BigDecimal.ONE, null, null);

    transaction.edit(
        LocalDate.now(),
        BigDecimal.TEN,
        CATEGORY_ID,
        CategoryType.EXPENSE,
        ACCOUNT_ID,
        PAYMENT_METHOD_ID,
        "Fuel",
        null,
        fuelDetails);

    assertThat(transaction.getFuelDetails()).isEqualTo(fuelDetails);
  }

  // F026 (ADR 0023): Transaction gains optional investmentHoldingId, carried through
  // create/edit/reconstitute unchanged otherwise - no cross-check here against the category
  // being the dividend category (an application-layer concern, same shape as fuelDetails).

  @Test
  void createWithoutInvestmentHoldingIdLeavesItNull() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Salary",
            null);

    assertThat(transaction.getInvestmentHoldingId()).isNull();
  }

  @Test
  void createWithInvestmentHoldingIdCarriesItThrough() {
    UUID holdingId = UUID.randomUUID();

    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Dividends",
            null,
            null,
            holdingId);

    assertThat(transaction.getInvestmentHoldingId()).isEqualTo(holdingId);
    assertThat(transaction.getFuelDetails()).isNull();
    // Every other field is exactly as it would be without an investment holding id.
    assertThat(transaction.getDescription()).isEqualTo("Dividends");
  }

  @Test
  void reconstituteCarriesTheInvestmentHoldingIdThrough() {
    UUID holdingId = UUID.randomUUID();

    Transaction transaction =
        Transaction.reconstitute(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Dividends",
            null,
            null,
            holdingId);

    assertThat(transaction.getInvestmentHoldingId()).isEqualTo(holdingId);
  }

  @Test
  void editWithoutInvestmentHoldingIdClearsAnyPreviouslyRecordedOne() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Dividends",
            null,
            null,
            UUID.randomUUID());

    transaction.edit(
        LocalDate.now(),
        BigDecimal.TEN,
        CATEGORY_ID,
        CategoryType.INCOME,
        ACCOUNT_ID,
        PAYMENT_METHOD_ID,
        "Salary",
        null);

    assertThat(transaction.getInvestmentHoldingId()).isNull();
  }

  @Test
  void editWithInvestmentHoldingIdReplacesAnyPreviousOne() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            CATEGORY_ID,
            CategoryType.INCOME,
            ACCOUNT_ID,
            PAYMENT_METHOD_ID,
            null,
            "Salary",
            null);
    UUID holdingId = UUID.randomUUID();

    transaction.edit(
        LocalDate.now(),
        BigDecimal.TEN,
        CATEGORY_ID,
        CategoryType.INCOME,
        ACCOUNT_ID,
        PAYMENT_METHOD_ID,
        "Dividends",
        null,
        null,
        holdingId);

    assertThat(transaction.getInvestmentHoldingId()).isEqualTo(holdingId);
  }
}
