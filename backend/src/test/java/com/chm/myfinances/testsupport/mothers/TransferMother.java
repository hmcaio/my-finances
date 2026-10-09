package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Test data builder for {@link Transfer} (issue #31, B6): a valid, in-memory transfer with sensible
 * defaults that a test overrides only where it cares. A test asserting on {@link Transfer#create}'s
 * own validation should keep calling {@code Transfer.create(...)} directly - going through this
 * builder would obscure what's being tested.
 *
 * <p>{@link #withTradeConfirmation} (F027, ADR 0024) switches {@link #build()} to {@link
 * Transfer#createTradeConfirmation} instead of the plain {@link Transfer#create}: {@code amount}/
 * direction are then derived from the confirmation's {@code netCost}, so {@link #withAmount}/
 * {@link #withFromAccountId}/{@link #withToAccountId} are ignored for a trade-confirmation build.
 */
public final class TransferMother {

  private UUID id = UUID.randomUUID();
  private LocalDate date = LocalDate.now();
  private UUID fromAccountId = UUID.randomUUID();
  private UUID toAccountId = UUID.randomUUID();
  private BigDecimal amount = new BigDecimal("100.00");
  private String description = "Credit card payment";
  private String additionalNotes = null;
  private UUID cashAccountId;
  private UUID investmentAccountId;
  private BigDecimal taxes;
  private List<TradeConfirmationLine> lines;

  private TransferMother() {}

  public static TransferMother transfer() {
    return new TransferMother();
  }

  public TransferMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public TransferMother withDate(LocalDate date) {
    this.date = date;
    return this;
  }

  public TransferMother withFromAccountId(UUID fromAccountId) {
    this.fromAccountId = fromAccountId;
    return this;
  }

  public TransferMother withToAccountId(UUID toAccountId) {
    this.toAccountId = toAccountId;
    return this;
  }

  public TransferMother withAmount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  public TransferMother withDescription(String description) {
    this.description = description;
    return this;
  }

  public TransferMother withAdditionalNotes(String additionalNotes) {
    this.additionalNotes = additionalNotes;
    return this;
  }

  /**
   * A single-line trade confirmation (F027): {@code cashAccountId}/{@code investmentAccountId} are
   * unlabeled - direction is derived from the line's net cost.
   */
  public TransferMother withTradeConfirmation(
      UUID cashAccountId, UUID investmentAccountId, BigDecimal taxes, TradeConfirmationLine line) {
    return withTradeConfirmation(cashAccountId, investmentAccountId, taxes, List.of(line));
  }

  public TransferMother withTradeConfirmation(
      UUID cashAccountId,
      UUID investmentAccountId,
      BigDecimal taxes,
      List<TradeConfirmationLine> lines) {
    this.cashAccountId = cashAccountId;
    this.investmentAccountId = investmentAccountId;
    this.taxes = taxes;
    this.lines = lines;
    return this;
  }

  public Transfer build() {
    if (lines != null) {
      return Transfer.createTradeConfirmation(
          id,
          date,
          cashAccountId,
          investmentAccountId,
          description,
          additionalNotes,
          taxes,
          TradeConfirmation.of(lines));
    }
    return Transfer.create(
        id, date, fromAccountId, toAccountId, amount, description, additionalNotes);
  }
}
