package com.chm.myfinances.domain.transfer;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Transfer aggregate (PRD S5.5, S6.2, F005 spec): movement of money between two of the user's own
 * accounts - most commonly paying a credit card statement from checking. Unlike {@code
 * Transaction}, a Transfer has no category and no budget impact (PRD S5.5) - it explicitly isn't
 * "categorized".
 *
 * <p>{@code amount} is always stored positive, same convention as {@code Transaction}. Its
 * two-sided balance effect (source decreases; destination's effect depends on the destination
 * account's own {@code AccountType}) is computed by F003's {@code AccountBalanceQuery}, not stored
 * here.
 *
 * <p>Invariant: {@code fromAccountId != toAccountId} - a transfer to the same account is
 * meaningless. Enforced here at the domain level (not just the API layer), same spirit as {@code
 * Account.requireOpen()}. Both accounts being open at creation time is an application-layer concern
 * (F005 spec) - checked by {@code TransferService} against F003's {@code AccountRepository}, since
 * the domain layer never depends on another aggregate's repository.
 *
 * <p>{@code description} (mandatory) and {@code additionalNotes} (optional) are bounded free-text
 * fields, sharing the {@link TextFieldConstraints} convention used by {@code Transaction} (F004).
 *
 * <p>F009 (ADR 0012) makes a transfer able to be a buy/sell: {@code investmentProductId} tags it
 * with the product bought or sold, and {@link InvestmentTradeDetails} records quantity, unit price
 * and taxes (record-only, never used in balances). Details require a product. That an endpoint is
 * an {@code INVESTMENT} account, and that the product belongs to it, are application-layer rules
 * ({@code TransferService}); this package never imports the account or product packages.
 *
 * <p>No versioning, no editing beyond a plain field update - deleting is a hard delete (F005 spec),
 * same as {@code Transaction}.
 */
public final class Transfer {

  private final UUID id;
  private LocalDate date;
  private UUID fromAccountId;
  private UUID toAccountId;
  private BigDecimal amount;
  private String description;
  private String additionalNotes;
  private UUID investmentProductId;
  private InvestmentTradeDetails tradeDetails;

  private Transfer(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      UUID investmentProductId,
      InvestmentTradeDetails tradeDetails) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
    this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId must not be null");
    requireDifferentAccounts(fromAccountId, toAccountId);
    this.amount = requireValidAmount(amount);
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
    this.tradeDetails = normalizeTradeDetails(investmentProductId, tradeDetails);
    this.investmentProductId = investmentProductId;
  }

  /**
   * Creates a brand-new plain (non-investment) Transfer. {@code id} comes from {@code IdGenerator}.
   */
  public static Transfer create(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    return create(
        id, date, fromAccountId, toAccountId, amount, description, additionalNotes, null, null);
  }

  /**
   * Creates a brand-new Transfer, optionally tagged with an investment product (a buy/sell, F009).
   * {@code tradeDetails} may be {@code null} (none) but requires a product when non-empty.
   */
  public static Transfer create(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      UUID investmentProductId,
      InvestmentTradeDetails tradeDetails) {
    return new Transfer(
        id,
        date,
        fromAccountId,
        toAccountId,
        amount,
        description,
        additionalNotes,
        investmentProductId,
        tradeDetails);
  }

  /** Rebuilds a Transfer from already-validated persisted state. */
  public static Transfer reconstitute(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      UUID investmentProductId,
      InvestmentTradeDetails tradeDetails) {
    return new Transfer(
        id,
        date,
        fromAccountId,
        toAccountId,
        amount,
        description,
        additionalNotes,
        investmentProductId,
        tradeDetails);
  }

  /** Edit of a plain transfer: clears any investment product and trade details. */
  public void edit(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    edit(date, fromAccountId, toAccountId, amount, description, additionalNotes, null, null);
  }

  /**
   * Plain in-place edit of every field except {@code id} (F005 spec, same as {@code Transaction}),
   * including the investment tag and trade details (full replace). Every value is validated before
   * any is assigned, so a rejected edit leaves the transfer untouched.
   */
  public void edit(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      UUID investmentProductId,
      InvestmentTradeDetails tradeDetails) {
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
    Objects.requireNonNull(toAccountId, "toAccountId must not be null");
    requireDifferentAccounts(fromAccountId, toAccountId);
    BigDecimal validAmount = requireValidAmount(amount);
    String validDescription = requireValidDescription(description);
    String validNotes = requireValidAdditionalNotes(additionalNotes);
    InvestmentTradeDetails validDetails = normalizeTradeDetails(investmentProductId, tradeDetails);
    this.date = date;
    this.fromAccountId = fromAccountId;
    this.toAccountId = toAccountId;
    this.amount = validAmount;
    this.description = validDescription;
    this.additionalNotes = validNotes;
    this.investmentProductId = investmentProductId;
    this.tradeDetails = validDetails;
  }

  /** Trade details describe a trade of a product, so they can't exist without one. */
  private static InvestmentTradeDetails normalizeTradeDetails(
      UUID investmentProductId, InvestmentTradeDetails tradeDetails) {
    InvestmentTradeDetails details =
        tradeDetails == null ? InvestmentTradeDetails.empty() : tradeDetails;
    if (investmentProductId == null && !details.isEmpty()) {
      throw new IllegalArgumentException("trade details require an investmentProductId");
    }
    return details;
  }

  private static void requireDifferentAccounts(UUID fromAccountId, UUID toAccountId) {
    if (fromAccountId.equals(toAccountId)) {
      throw new IllegalArgumentException("fromAccountId and toAccountId must not be the same");
    }
  }

  private static BigDecimal requireValidAmount(BigDecimal value) {
    Objects.requireNonNull(value, "amount must not be null");
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    return value;
  }

  private static String requireValidDescription(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("description must not be blank");
    }
    if (value.length() > TextFieldConstraints.MAX_DESCRIPTION_LENGTH) {
      throw new IllegalArgumentException(
          "description must not exceed "
              + TextFieldConstraints.MAX_DESCRIPTION_LENGTH
              + " characters");
    }
    return value;
  }

  private static String requireValidAdditionalNotes(String value) {
    if (value != null && value.length() > TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) {
      throw new IllegalArgumentException(
          "additionalNotes must not exceed "
              + TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH
              + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public LocalDate getDate() {
    return date;
  }

  public UUID getFromAccountId() {
    return fromAccountId;
  }

  public UUID getToAccountId() {
    return toAccountId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getDescription() {
    return description;
  }

  public String getAdditionalNotes() {
    return additionalNotes;
  }

  /** The investment product this transfer buys or sells, or {@code null} for a plain transfer. */
  public UUID getInvestmentProductId() {
    return investmentProductId;
  }

  /** Never {@code null}; {@link InvestmentTradeDetails#isEmpty()} when nothing was recorded. */
  public InvestmentTradeDetails getTradeDetails() {
    return tradeDetails;
  }
}
