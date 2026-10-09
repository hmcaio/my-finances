package com.chm.myfinances.domain.transfer;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
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
 * <p>F027 (ADR 0024, superseding F009/ADR 0012's one-product-per-transfer shape): a transfer can
 * carry a {@link TradeConfirmation} - one or more product lines sharing one cash account and one
 * {@code INVESTMENT} account, plus a single {@code taxes} figure that covers the whole settlement
 * (never apportioned per line). {@link #createTradeConfirmation}/{@link #editTradeConfirmation}
 * derive {@code amount} and direction ({@code fromAccountId}/{@code toAccountId}) from {@link
 * TradeConfirmation#netCost}, rather than trusting a user-typed figure - unlike the plain {@link
 * #create}/{@link #edit} overloads, which still take {@code amount}/direction manually and are used
 * whenever no confirmation is given. That an endpoint is an {@code INVESTMENT} account, and that
 * every line's product has an open holding there, are application-layer rules ({@code
 * TransferService}); this package never imports the account, product or holding packages.
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
  private BigDecimal taxes;
  private TradeConfirmation tradeConfirmation;

  private Transfer(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      BigDecimal taxes,
      TradeConfirmation tradeConfirmation) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
    this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId must not be null");
    requireDifferentAccounts(fromAccountId, toAccountId);
    this.amount = requireValidAmount(amount);
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
    this.tradeConfirmation = tradeConfirmation;
    this.taxes = requireValidTaxes(tradeConfirmation, taxes);
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
    return new Transfer(
        id, date, fromAccountId, toAccountId, amount, description, additionalNotes, null, null);
  }

  /**
   * Creates a brand-new Transfer carrying a {@link TradeConfirmation} (F027, ADR 0024): {@code
   * amount} and direction are derived, never taken from the caller. {@code cashAccountId}/{@code
   * investmentAccountId} are unlabeled (which is "from" and which is "to" depends on {@link
   * TradeConfirmation#netCost}'s sign) - a positive net cost flows cash -&gt; investment, a
   * negative one (net proceeds) flows investment -&gt; cash.
   */
  public static Transfer createTradeConfirmation(
      UUID id,
      LocalDate date,
      UUID cashAccountId,
      UUID investmentAccountId,
      String description,
      String additionalNotes,
      BigDecimal taxes,
      TradeConfirmation confirmation) {
    Objects.requireNonNull(confirmation, "confirmation must not be null");
    BigDecimal net = confirmation.netCost(taxes);
    boolean isCost = net.signum() > 0;
    UUID fromAccountId = isCost ? cashAccountId : investmentAccountId;
    UUID toAccountId = isCost ? investmentAccountId : cashAccountId;
    return new Transfer(
        id,
        date,
        fromAccountId,
        toAccountId,
        net.abs(),
        description,
        additionalNotes,
        taxes,
        confirmation);
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
      BigDecimal taxes,
      TradeConfirmation tradeConfirmation) {
    return new Transfer(
        id,
        date,
        fromAccountId,
        toAccountId,
        amount,
        description,
        additionalNotes,
        taxes,
        tradeConfirmation);
  }

  /** Edit of a plain transfer: clears any trade confirmation and taxes. */
  public void edit(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
    Objects.requireNonNull(toAccountId, "toAccountId must not be null");
    requireDifferentAccounts(fromAccountId, toAccountId);
    BigDecimal validAmount = requireValidAmount(amount);
    String validDescription = requireValidDescription(description);
    String validNotes = requireValidAdditionalNotes(additionalNotes);
    this.date = date;
    this.fromAccountId = fromAccountId;
    this.toAccountId = toAccountId;
    this.amount = validAmount;
    this.description = validDescription;
    this.additionalNotes = validNotes;
    this.taxes = null;
    this.tradeConfirmation = null;
  }

  /**
   * Full-replace edit of a Transfer carrying a {@link TradeConfirmation} (F027): recomputes {@code
   * amount}/direction from the new confirmation's {@code netCost}, same derivation as {@link
   * #createTradeConfirmation}. Every value is validated before any is assigned, so a rejected edit
   * leaves the transfer untouched.
   */
  public void editTradeConfirmation(
      LocalDate date,
      UUID cashAccountId,
      UUID investmentAccountId,
      String description,
      String additionalNotes,
      BigDecimal taxes,
      TradeConfirmation confirmation) {
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(cashAccountId, "cashAccountId must not be null");
    Objects.requireNonNull(investmentAccountId, "investmentAccountId must not be null");
    Objects.requireNonNull(confirmation, "confirmation must not be null");
    BigDecimal net = confirmation.netCost(taxes);
    boolean isCost = net.signum() > 0;
    UUID fromAccountId = isCost ? cashAccountId : investmentAccountId;
    UUID toAccountId = isCost ? investmentAccountId : cashAccountId;
    requireDifferentAccounts(fromAccountId, toAccountId);
    BigDecimal validAmount = requireValidAmount(net.abs());
    String validDescription = requireValidDescription(description);
    String validNotes = requireValidAdditionalNotes(additionalNotes);
    BigDecimal validTaxes = requireValidTaxes(confirmation, taxes);
    this.date = date;
    this.fromAccountId = fromAccountId;
    this.toAccountId = toAccountId;
    this.amount = validAmount;
    this.description = validDescription;
    this.additionalNotes = validNotes;
    this.taxes = validTaxes;
    this.tradeConfirmation = confirmation;
  }

  /**
   * A trade confirmation describes a settlement, so it can't exist without taxes to net against
   * (the confirmation's one aggregate tax figure); conversely, taxes without a confirmation is
   * meaningless (there is nothing for it to net against). Mirrors the old "trade details require a
   * product" pairing rule (ADR 0012) at the new granularity.
   */
  private static BigDecimal requireValidTaxes(TradeConfirmation confirmation, BigDecimal taxes) {
    if (confirmation == null) {
      if (taxes != null) {
        throw new IllegalArgumentException("taxes require a tradeConfirmation");
      }
      return null;
    }
    Objects.requireNonNull(taxes, "a tradeConfirmation requires taxes");
    if (taxes.signum() < 0) {
      throw new IllegalArgumentException("taxes must not be negative");
    }
    return taxes;
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

  /** The settlement's aggregate tax/fee figure; {@code null} for a plain transfer. */
  public BigDecimal getTaxes() {
    return taxes;
  }

  /** Empty for a plain transfer. */
  public Optional<TradeConfirmation> getTradeConfirmation() {
    return Optional.ofNullable(tradeConfirmation);
  }
}
