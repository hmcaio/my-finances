package com.chm.myfinances.domain.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction aggregate (PRD S5.3, F004 spec). The core ledger entry: every account's running
 * balance (F003's {@code AccountBalanceQuery}) is computed by summing these.
 *
 * <p>{@code amount} is always stored positive - whether it increases or decreases the account's
 * balance is derived from {@code type} and the account's {@code AccountType} at read time (F003
 * spec), not duplicated here.
 *
 * <p>{@code type} is captured at creation (denormalized from the category's own, already-immutable
 * type - F002 spec) rather than always re-read from the category, so a stable, redundant
 * data-integrity check is available even though {@code Category.type} can never itself change.
 *
 * <p>{@code recurringTemplateVersionId} is set once, at creation, and never touched by {@link
 * #edit}: PRD S5.7 - "a single past transaction can still be hand-edited directly (amount/date/
 * account) without touching the version chain". F007 doesn't exist yet, so this is always {@code
 * null} for now, but the field/column already exists ahead of it.
 *
 * <p>Editing (amount/date/category/account/payment method/description/additional notes) is a plain
 * in-place update - no versioning, unlike Budget/RecurringTemplate (F004 spec). Deleting is a hard
 * delete - a transaction has no downstream history that would be orphaned by removing it.
 *
 * <p>{@code description} (mandatory) and {@code additionalNotes} (optional) are bounded free-text
 * fields, sharing the {@link TextFieldConstraints} convention used for taxonomy "name" fields
 * (F002/F003): a length check here, a matching {@code @Size} on the request DTOs, and a matching
 * {@code varchar(n)} column.
 *
 * <p>F024 (ADR 0021) makes a transaction able to carry fuel-purchase details: {@link
 * #getFuelDetails()} is {@code null} for an ordinary transaction and non-null exactly when the
 * transaction records a fuel purchase. Nothing here enforces that it lines up with the category
 * being the dedicated fuel category - that cross-aggregate invariant is an application-layer
 * concern ({@code TransactionService}), since this package never imports {@code domain.category}
 * beyond the shared {@link CategoryType} enum, and never imports {@code domain.vehicle} at all.
 */
public final class Transaction {

  private final UUID id;
  private LocalDate date;
  private BigDecimal amount;
  private UUID categoryId;
  private CategoryType type;
  private UUID accountId;
  private UUID paymentMethodId;
  private final UUID recurringTemplateVersionId;
  private String description;
  private String additionalNotes;
  private FuelDetails fuelDetails;
  private UUID investmentHoldingId;

  private Transaction(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails,
      UUID investmentHoldingId) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.amount = requireValidAmount(amount);
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.paymentMethodId =
        Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
    this.recurringTemplateVersionId = recurringTemplateVersionId;
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
    this.fuelDetails = fuelDetails;
    this.investmentHoldingId = investmentHoldingId;
  }

  /**
   * Creates a brand-new Transaction with no fuel details. {@code id} must come from the {@code
   * IdGenerator} port.
   */
  public static Transaction create(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes) {
    return create(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes,
        null);
  }

  /**
   * Creates a brand-new Transaction, optionally carrying fuel-purchase details (F024). {@code
   * fuelDetails} may be {@code null} (not a fuel purchase). Never carries an investment holding
   * reference - callers that need one use the 12-argument overload (F026).
   */
  public static Transaction create(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails) {
    return create(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes,
        fuelDetails,
        null);
  }

  /**
   * Creates a brand-new Transaction, optionally carrying fuel-purchase details (F024) and/or an
   * investment holding reference (F026 - a dividend). The two are never both present in practice (a
   * cross-aggregate invariant enforced by {@code TransactionService}, not here); this constructor
   * accepts either independently, like {@code Transfer.create}'s fullest overload.
   */
  public static Transaction create(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails,
      UUID investmentHoldingId) {
    return new Transaction(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes,
        fuelDetails,
        investmentHoldingId);
  }

  /**
   * Rebuilds a Transaction, including its investment holding reference (F026), from persisted
   * state.
   */
  public static Transaction reconstitute(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails,
      UUID investmentHoldingId) {
    return new Transaction(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes,
        fuelDetails,
        investmentHoldingId);
  }

  /**
   * Plain in-place edit of every field except {@code id} and {@code recurringTemplateVersionId}
   * (F004 spec), clearing any fuel details. {@code categoryId}/{@code type} are updated together -
   * the caller (application layer) re-derives {@code type} from whatever category is being
   * assigned, same as at creation.
   */
  public void edit(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes) {
    edit(
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        description,
        additionalNotes,
        null);
  }

  /**
   * Full-replace edit including fuel details (F024), with no investment holding reference - same
   * "every field required, this one optional" shape as {@code Transfer.edit}. Passing {@code null}
   * for {@code fuelDetails} clears any previously recorded fuel purchase.
   */
  public void edit(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails) {
    edit(
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        description,
        additionalNotes,
        fuelDetails,
        null);
  }

  /**
   * Full-replace edit including fuel details (F024) and/or an investment holding reference (F026) -
   * the overload the controller calls. Passing {@code null} for either clears any previously
   * recorded value.
   */
  public void edit(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails,
      UUID investmentHoldingId) {
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.amount = requireValidAmount(amount);
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.paymentMethodId =
        Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
    this.fuelDetails = fuelDetails;
    this.investmentHoldingId = investmentHoldingId;
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

  public BigDecimal getAmount() {
    return amount;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public CategoryType getType() {
    return type;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getPaymentMethodId() {
    return paymentMethodId;
  }

  public UUID getRecurringTemplateVersionId() {
    return recurringTemplateVersionId;
  }

  public String getDescription() {
    return description;
  }

  public String getAdditionalNotes() {
    return additionalNotes;
  }

  /** Fuel-purchase details (F024), or {@code null} for an ordinary (non-fuel) transaction. */
  public FuelDetails getFuelDetails() {
    return fuelDetails;
  }

  /**
   * The {@code InvestmentHolding} this transaction is attributed to (F026) - present if and only if
   * the transaction's category is the dedicated dividend category (an application-layer invariant,
   * {@code TransactionService}). {@code null} for an ordinary transaction.
   */
  public UUID getInvestmentHoldingId() {
    return investmentHoldingId;
  }

  /**
   * Flat snapshot of every persisted field (F025 spec, ADR 0022), used to compute a before/after
   * diff for the audit log. Fuel details (F024) are flattened to their own top-level keys rather
   * than nested, matching how {@code TransactionJpaEntity} stores them as flat columns; a {@code
   * null} {@code fuelDetails} still contributes those keys with {@code null} values so a fuel
   * purchase being cleared (or added) shows up as an ordinary field-by-field change rather than
   * keys appearing/disappearing from the map.
   */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("date", date.toString());
    snapshot.put("amount", amount);
    snapshot.put("categoryId", categoryId.toString());
    snapshot.put("type", type.name());
    snapshot.put("accountId", accountId.toString());
    snapshot.put("paymentMethodId", paymentMethodId.toString());
    snapshot.put(
        "recurringTemplateVersionId",
        recurringTemplateVersionId == null ? null : recurringTemplateVersionId.toString());
    snapshot.put("description", description);
    snapshot.put("additionalNotes", additionalNotes);
    snapshot.put("vehicleId", fuelDetails == null ? null : fuelDetails.vehicleId().toString());
    snapshot.put("fuelType", fuelDetails == null ? null : fuelDetails.fuelType().name());
    snapshot.put("liters", fuelDetails == null ? null : fuelDetails.liters());
    snapshot.put("pricePerLiter", fuelDetails == null ? null : fuelDetails.pricePerLiter());
    snapshot.put("kmSinceLastFill", fuelDetails == null ? null : fuelDetails.kmSinceLastFill());
    snapshot.put("odometer", fuelDetails == null ? null : fuelDetails.odometer());
    snapshot.put(
        "investmentHoldingId", investmentHoldingId == null ? null : investmentHoldingId.toString());
    return snapshot;
  }
}
