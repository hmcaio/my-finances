package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test data builder for {@link Transfer} (issue #31, B6): a valid, in-memory transfer with sensible
 * defaults that a test overrides only where it cares. A test asserting on {@link Transfer#create}'s
 * own validation should keep calling {@code Transfer.create(...)} directly - going through this
 * builder would obscure what's being tested.
 */
public final class TransferMother {

  private UUID id = UUID.randomUUID();
  private LocalDate date = LocalDate.now();
  private UUID fromAccountId = UUID.randomUUID();
  private UUID toAccountId = UUID.randomUUID();
  private BigDecimal amount = new BigDecimal("100.00");
  private String description = "Credit card payment";
  private String additionalNotes = null;

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

  public Transfer build() {
    return Transfer.create(
        id, date, fromAccountId, toAccountId, amount, description, additionalNotes);
  }
}
