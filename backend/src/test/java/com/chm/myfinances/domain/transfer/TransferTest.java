package com.chm.myfinances.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Transfer} (PRD S5.5/S6.2, F005 spec). Pure JUnit - no Spring
 * context, no database (ADR 0004) - written before {@link Transfer} itself, per F005's plan.md.
 */
class TransferTest {

  private static final UUID FROM_ACCOUNT_ID = UUID.randomUUID();
  private static final UUID TO_ACCOUNT_ID = UUID.randomUUID();

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 3, 15);

    Transfer transfer =
        Transfer.create(
            id,
            date,
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            new BigDecimal("100.00"),
            "Credit card payment",
            "Paid from checking");

    assertThat(transfer.getId()).isEqualTo(id);
    assertThat(transfer.getDate()).isEqualTo(date);
    assertThat(transfer.getFromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
    assertThat(transfer.getToAccountId()).isEqualTo(TO_ACCOUNT_ID);
    assertThat(transfer.getAmount()).isEqualByComparingTo("100.00");
    assertThat(transfer.getDescription()).isEqualTo("Credit card payment");
    assertThat(transfer.getAdditionalNotes()).isEqualTo("Paid from checking");
  }

  @Test
  void createAllowsNullAdditionalNotes() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.now(),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Transfer",
            null);

    assertThat(transfer.getAdditionalNotes()).isNull();
  }

  @Test
  void createRejectsSameFromAndToAccount() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    FROM_ACCOUNT_ID,
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullDate() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    null,
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullFromAccountId() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    null,
                    TO_ACCOUNT_ID,
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullToAccountId() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    null,
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullAmount() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    null,
                    "Transfer",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsZeroAmount() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.ZERO,
                    "Transfer",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNegativeAmount() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    new BigDecimal("-10.00"),
                    "Transfer",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullDescription() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.TEN,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsBlankDescription() {
    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.TEN,
                    "   ",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsDescriptionExceedingMaxLength() {
    String tooLong = "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1);

    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.TEN,
                    tooLong,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAdditionalNotesExceedingMaxLength() {
    String tooLong = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1);

    assertThatThrownBy(
            () ->
                Transfer.create(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.TEN,
                    "Transfer",
                    tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editUpdatesEveryEditableField() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Original description",
            "Original note");

    UUID newFromAccountId = UUID.randomUUID();
    UUID newToAccountId = UUID.randomUUID();
    transfer.edit(
        LocalDate.of(2026, 2, 2),
        newFromAccountId,
        newToAccountId,
        new BigDecimal("99.99"),
        "Updated description",
        "Updated note");

    assertThat(transfer.getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(transfer.getFromAccountId()).isEqualTo(newFromAccountId);
    assertThat(transfer.getToAccountId()).isEqualTo(newToAccountId);
    assertThat(transfer.getAmount()).isEqualByComparingTo("99.99");
    assertThat(transfer.getDescription()).isEqualTo("Updated description");
    assertThat(transfer.getAdditionalNotes()).isEqualTo("Updated note");
  }

  @Test
  void editDoesNotTouchId() {
    UUID id = UUID.randomUUID();
    Transfer transfer =
        Transfer.create(
            id,
            LocalDate.of(2026, 1, 1),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Transfer",
            null);

    transfer.edit(
        LocalDate.of(2026, 3, 3),
        UUID.randomUUID(),
        UUID.randomUUID(),
        BigDecimal.ONE,
        "Hand-edited",
        null);

    assertThat(transfer.getId()).isEqualTo(id);
  }

  @Test
  void editRejectsSameFromAndToAccount() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.now(),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Transfer",
            null);

    assertThatThrownBy(
            () ->
                transfer.edit(
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    FROM_ACCOUNT_ID,
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsZeroOrNegativeAmount() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.now(),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Transfer",
            null);

    assertThatThrownBy(
            () ->
                transfer.edit(
                    LocalDate.now(),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    BigDecimal.ZERO,
                    "Transfer",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsBlankDescription() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.now(),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Transfer",
            null);

    assertThatThrownBy(
            () ->
                transfer.edit(
                    LocalDate.now(), FROM_ACCOUNT_ID, TO_ACCOUNT_ID, BigDecimal.TEN, "   ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRebuildsWithoutRevalidatingBusinessState() {
    UUID id = UUID.randomUUID();
    Transfer transfer =
        Transfer.reconstitute(
            id,
            LocalDate.of(2026, 1, 1),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            new BigDecimal("50.00"),
            "Reconstituted",
            "Notes",
            null,
            null);

    assertThat(transfer.getId()).isEqualTo(id);
    assertThat(transfer.getAmount()).isEqualByComparingTo("50.00");
    assertThat(transfer.getDescription()).isEqualTo("Reconstituted");
    assertThat(transfer.getAdditionalNotes()).isEqualTo("Notes");
    assertThat(transfer.getTaxes()).isNull();
    assertThat(transfer.getTradeConfirmation()).isEmpty();
  }

  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID OTHER_PRODUCT_ID = UUID.randomUUID();

  private static TradeConfirmationLine buyLine(UUID productId, String quantity, String unitPrice) {
    return new TradeConfirmationLine(
        productId, TradeSide.BUY, new BigDecimal(quantity), new BigDecimal(unitPrice), null, false);
  }

  private static TradeConfirmationLine sellLine(UUID productId, String quantity, String unitPrice) {
    return new TradeConfirmationLine(
        productId,
        TradeSide.SELL,
        new BigDecimal(quantity),
        new BigDecimal(unitPrice),
        null,
        false);
  }

  @Test
  void plainTransferHasNoTaxesAndNoTradeConfirmation() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.now(),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            BigDecimal.TEN,
            "Transfer",
            null);

    assertThat(transfer.getTaxes()).isNull();
    assertThat(transfer.getTradeConfirmation()).isEmpty();
  }

  @Test
  void createTradeConfirmationDerivesFromAccountToAccountAndAmountFromANetCost() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(buyLine(PRODUCT_ID, "10", "100.00")));

    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            "Buy",
            null,
            new BigDecimal("5.00"),
            confirmation);

    // Net cost is positive (1005.00): cash -> investment.
    assertThat(transfer.getFromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
    assertThat(transfer.getToAccountId()).isEqualTo(TO_ACCOUNT_ID);
    assertThat(transfer.getAmount()).isEqualByComparingTo("1005.00");
    assertThat(transfer.getTaxes()).isEqualByComparingTo("5.00");
    assertThat(transfer.getTradeConfirmation()).contains(confirmation);
  }

  @Test
  void createTradeConfirmationReversesDirectionForANetSell() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(sellLine(PRODUCT_ID, "10", "100.00")));

    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID, // cashAccountId
            TO_ACCOUNT_ID, // investmentAccountId
            "Sell",
            null,
            new BigDecimal("5.00"),
            confirmation);

    // Net cost is negative (-995.00): investment -> cash.
    assertThat(transfer.getFromAccountId()).isEqualTo(TO_ACCOUNT_ID);
    assertThat(transfer.getToAccountId()).isEqualTo(FROM_ACCOUNT_ID);
    assertThat(transfer.getAmount()).isEqualByComparingTo("995.00");
  }

  @Test
  void createTradeConfirmationRejectsAnExactZeroNetSettlement() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                buyLine(PRODUCT_ID, "10", "100.00"), sellLine(OTHER_PRODUCT_ID, "10", "100.00")));

    assertThatThrownBy(
            () ->
                Transfer.createTradeConfirmation(
                    UUID.randomUUID(),
                    LocalDate.of(2026, 3, 15),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    "Wash",
                    null,
                    BigDecimal.ZERO,
                    confirmation))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createTradeConfirmationRejectsNullConfirmation() {
    assertThatThrownBy(
            () ->
                Transfer.createTradeConfirmation(
                    UUID.randomUUID(),
                    LocalDate.of(2026, 3, 15),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    "Buy",
                    null,
                    BigDecimal.ZERO,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void editTradeConfirmationRecomputesAmountAndDirection() {
    TradeConfirmation original = TradeConfirmation.of(List.of(buyLine(PRODUCT_ID, "10", "100.00")));
    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            "Buy",
            null,
            new BigDecimal("5.00"),
            original);

    TradeConfirmation edited = TradeConfirmation.of(List.of(sellLine(PRODUCT_ID, "10", "100.00")));
    transfer.editTradeConfirmation(
        LocalDate.of(2026, 3, 16),
        FROM_ACCOUNT_ID,
        TO_ACCOUNT_ID,
        "Now a sell",
        "note",
        new BigDecimal("2.00"),
        edited);

    assertThat(transfer.getFromAccountId()).isEqualTo(TO_ACCOUNT_ID);
    assertThat(transfer.getToAccountId()).isEqualTo(FROM_ACCOUNT_ID);
    assertThat(transfer.getAmount()).isEqualByComparingTo("998.00");
    assertThat(transfer.getTaxes()).isEqualByComparingTo("2.00");
    assertThat(transfer.getTradeConfirmation()).contains(edited);
    assertThat(transfer.getDescription()).isEqualTo("Now a sell");
  }

  @Test
  void editTradeConfirmationRejectingTheNewConfirmationLeavesTheTransferUntouched() {
    TradeConfirmation original = TradeConfirmation.of(List.of(buyLine(PRODUCT_ID, "10", "100.00")));
    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            "Buy",
            null,
            new BigDecimal("5.00"),
            original);
    TradeConfirmation washConfirmation =
        TradeConfirmation.of(
            List.of(
                buyLine(PRODUCT_ID, "10", "100.00"), sellLine(OTHER_PRODUCT_ID, "10", "100.00")));

    assertThatThrownBy(
            () ->
                transfer.editTradeConfirmation(
                    LocalDate.of(2026, 3, 16),
                    FROM_ACCOUNT_ID,
                    TO_ACCOUNT_ID,
                    "Changed",
                    null,
                    BigDecimal.ZERO,
                    washConfirmation))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(transfer.getDescription()).isEqualTo("Buy");
    assertThat(transfer.getTradeConfirmation()).contains(original);
  }

  @Test
  void editClearsATradeConfirmationBackToAPlainTransfer() {
    TradeConfirmation original = TradeConfirmation.of(List.of(buyLine(PRODUCT_ID, "10", "100.00")));
    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            "Buy",
            null,
            new BigDecimal("5.00"),
            original);

    transfer.edit(
        LocalDate.of(2026, 3, 17),
        FROM_ACCOUNT_ID,
        TO_ACCOUNT_ID,
        BigDecimal.TEN,
        "Plain again",
        null);

    assertThat(transfer.getTaxes()).isNull();
    assertThat(transfer.getTradeConfirmation()).isEmpty();
  }

  @Test
  void toAuditSnapshotOfAPlainTransferHasNoTradeConfirmationLines() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            new BigDecimal("50.00"),
            "Pay card",
            "Note");

    Map<String, Object> snapshot = transfer.toAuditSnapshot();

    assertThat(snapshot)
        .containsEntry("date", "2026-03-15")
        .containsEntry("fromAccountId", FROM_ACCOUNT_ID.toString())
        .containsEntry("toAccountId", TO_ACCOUNT_ID.toString())
        .containsEntry("amount", new BigDecimal("50.00"))
        .containsEntry("description", "Pay card")
        .containsEntry("additionalNotes", "Note")
        .containsEntry("taxes", null)
        .containsEntry("tradeConfirmationLines", null);
  }

  @Test
  void toAuditSnapshotOfATradeConfirmationFlattensItsLines() {
    TradeConfirmation confirmation =
        TradeConfirmation.of(List.of(buyLine(PRODUCT_ID, "10", "100.00")));
    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            "Buy",
            null,
            new BigDecimal("5.00"),
            confirmation);

    Map<String, Object> snapshot = transfer.toAuditSnapshot();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines =
        (List<Map<String, Object>>) snapshot.get("tradeConfirmationLines");
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0))
        .containsEntry("productId", PRODUCT_ID.toString())
        .containsEntry("side", "BUY")
        .containsEntry("quantity", new BigDecimal("10"))
        .containsEntry("unitPrice", new BigDecimal("100.00"))
        .containsEntry("closeHolding", false);
    assertThat(snapshot).containsEntry("taxes", new BigDecimal("5.00"));
  }
}
