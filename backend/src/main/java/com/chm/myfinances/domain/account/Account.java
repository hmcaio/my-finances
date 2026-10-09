package com.chm.myfinances.domain.account;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Account aggregate (PRD S5.4, F003 spec). The central entity almost every other feature
 * references: transactions (F004) and transfers (F005) post activity against it, and running
 * balance / net worth are computed on top of it.
 *
 * <p>{@code type}, {@code openingBalance}, and {@code openingBalanceDate} are fixed at creation and
 * deliberately have no mutator anywhere on this class - retroactively changing them would silently
 * rewrite every past balance/net-worth calculation. If the user made a data-entry mistake, the fix
 * is deleting and recreating the account before any activity exists, not editing it after the fact
 * (F003 spec). {@code name}/{@code institutionId} may be edited at any time via {@link
 * #edit(String, UUID)}, including after the account is closed (closing only blocks new *activity* -
 * transactions/transfers - not a metadata correction).
 *
 * <p>{@code openingBalance}/{@code openingBalanceDate} are {@code null} exactly when {@code type ==
 * INVESTMENT} (F008, ADR 0012: its value comes only from snapshots) and required for every other
 * type, so their getters can return {@code null} and callers must cope.
 *
 * <p>The institution is held by id only and is required (F017): this class deliberately doesn't
 * import {@code domain/institution}. That the institution exists is checked in {@code
 * AccountService}, the same way other cross-aggregate references are.
 */
public final class Account {

  private final UUID id;
  private String name;
  private UUID institutionId;
  private final AccountType type;
  private final BigDecimal openingBalance;
  private final LocalDate openingBalanceDate;
  private LocalDate closedDate;

  private Account(
      UUID id,
      String name,
      UUID institutionId,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate,
      LocalDate closedDate) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireValidName(name);
    this.institutionId = Objects.requireNonNull(institutionId, "institutionId must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    if (type == AccountType.INVESTMENT) {
      if (openingBalance != null || openingBalanceDate != null) {
        throw new IllegalArgumentException(
            "an INVESTMENT account must not have an opening balance or opening balance date");
      }
      this.openingBalance = null;
      this.openingBalanceDate = null;
    } else {
      this.openingBalance =
          Objects.requireNonNull(openingBalance, "openingBalance must not be null");
      this.openingBalanceDate =
          Objects.requireNonNull(openingBalanceDate, "openingBalanceDate must not be null");
    }
    this.closedDate = closedDate;
  }

  /** Creates a brand-new, open Account. {@code id} must come from the {@code IdGenerator} port. */
  public static Account create(
      UUID id,
      String name,
      UUID institutionId,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate) {
    return new Account(id, name, institutionId, type, openingBalance, openingBalanceDate, null);
  }

  /** Rebuilds an Account from already-validated persisted state. */
  public static Account reconstitute(
      UUID id,
      String name,
      UUID institutionId,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate,
      LocalDate closedDate) {
    return new Account(
        id, name, institutionId, type, openingBalance, openingBalanceDate, closedDate);
  }

  /**
   * Edits name/institution only - the only mutation this aggregate exposes (F003 spec). Both values
   * are validated before either is assigned, so a rejected edit leaves the account untouched.
   */
  public void edit(String newName, UUID newInstitutionId) {
    String validName = requireValidName(newName);
    UUID validInstitutionId =
        Objects.requireNonNull(newInstitutionId, "institutionId must not be null");
    this.name = validName;
    this.institutionId = validInstitutionId;
  }

  /**
   * Sets {@code closedDate}, marking the account closed. Closing an already-closed account is
   * rejected - {@code closedDate} is set once, same spirit as opening balance/date. Takes the date
   * as a parameter rather than reading {@code LocalDate.now()} itself - this class stays
   * framework-free (ADR 0004/0005), so "today" comes from the caller (F003's {@code
   * AccountService}, via its injected {@code Clock}).
   */
  public void close(LocalDate closedDate) {
    if (isClosed()) {
      throw new IllegalStateException("Account is already closed: " + id);
    }
    this.closedDate = Objects.requireNonNull(closedDate, "closedDate must not be null");
  }

  public boolean isClosed() {
    return closedDate != null;
  }

  /**
   * Guard called before attaching new activity (a transaction from F004, a transfer from F005) to
   * this account. Enforced here at the domain level - not just the API layer - per F003 spec, so no
   * future caller can accidentally bypass it.
   */
  public void requireOpen() {
    if (isClosed()) {
      throw new IllegalStateException("Account is closed and cannot accept new activity: " + id);
    }
  }

  private static String requireValidName(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (value.length() > TextFieldConstraints.MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "name must not exceed " + TextFieldConstraints.MAX_NAME_LENGTH + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public UUID getInstitutionId() {
    return institutionId;
  }

  public AccountType getType() {
    return type;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public LocalDate getOpeningBalanceDate() {
    return openingBalanceDate;
  }

  public LocalDate getClosedDate() {
    return closedDate;
  }

  /**
   * Flat snapshot of every persisted field (F025 spec, ADR 0022), used to compute a before/after
   * diff for the audit log. {@code type} is included even though it's immutable - still a persisted
   * field the completeness test must account for, not that it ever actually changes.
   */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("name", name);
    snapshot.put("institutionId", institutionId.toString());
    snapshot.put("type", type.name());
    snapshot.put("openingBalance", openingBalance);
    snapshot.put(
        "openingBalanceDate", openingBalanceDate == null ? null : openingBalanceDate.toString());
    snapshot.put("closedDate", closedDate == null ? null : closedDate.toString());
    return snapshot;
  }
}
