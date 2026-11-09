package com.chm.myfinances.domain.goal;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Goal aggregate (F039 spec, ADR 0034): something to save toward - a target amount, an optional
 * target date, a planned monthly contribution and three real annual return assumptions
 * (pessimistic, base, optimistic, in percent). The products allocated to it are the separate {@link
 * GoalAllocation} aggregate. Edited in place, not versioned (ADR 0034).
 *
 * <p>Invariants (rejected with {@link IllegalArgumentException}, which {@code GoalService}
 * translates to a 400): a non-blank name within {@link TextFieldConstraints#MAX_NAME_LENGTH}, notes
 * within {@link TextFieldConstraints#MAX_ADDITIONAL_NOTES_LENGTH}, a positive target, a
 * non-negative contribution (zero is a valid plan: let the balance grow, the one amount with {@code
 * >= 0}, docs/conventions/data-constraints.md), each return within {@code -50..100} and ordered
 * {@code pessimistic <= base <= optimistic}. Money and percentages carry at most two decimals and
 * are padded to that scale, so a value replaced with the same figures is a no-op for the audit diff
 * (which compares by {@code equals}).
 *
 * <p>{@code showOnDashboard} is false whenever the status is {@link GoalStatus#ARCHIVED}: {@link
 * #archive()} and {@link #edit} clear it, and turning it on for an archived goal throws {@link
 * IllegalStateException}. Restoring a goal does not turn it back on. That the target date is in the
 * future is a {@code GoalService} check (the domain has no clock).
 */
public final class Goal {

  private static final int SCALE = 2;
  private static final BigDecimal MIN_RETURN = new BigDecimal("-50");
  private static final BigDecimal MAX_RETURN = new BigDecimal("100");

  private final UUID id;
  private String name;
  private String notes;
  private BigDecimal targetAmount;
  private LocalDate targetDate;
  private BigDecimal plannedMonthlyContribution;
  private BigDecimal returnPessimistic;
  private BigDecimal returnBase;
  private BigDecimal returnOptimistic;
  private GoalStatus status;
  private boolean showOnDashboard;

  private Goal(
      UUID id,
      String name,
      String notes,
      BigDecimal targetAmount,
      LocalDate targetDate,
      BigDecimal plannedMonthlyContribution,
      BigDecimal returnPessimistic,
      BigDecimal returnBase,
      BigDecimal returnOptimistic,
      GoalStatus status,
      boolean showOnDashboard) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    apply(
        name,
        notes,
        targetAmount,
        targetDate,
        plannedMonthlyContribution,
        returnPessimistic,
        returnBase,
        returnOptimistic,
        status,
        showOnDashboard);
  }

  /** Creates a brand-new {@code ACTIVE} goal. {@code id} must come from the {@code IdGenerator}. */
  public static Goal create(
      UUID id,
      String name,
      String notes,
      BigDecimal targetAmount,
      LocalDate targetDate,
      BigDecimal plannedMonthlyContribution,
      BigDecimal returnPessimistic,
      BigDecimal returnBase,
      BigDecimal returnOptimistic,
      boolean showOnDashboard) {
    return new Goal(
        id,
        name,
        notes,
        targetAmount,
        targetDate,
        plannedMonthlyContribution,
        returnPessimistic,
        returnBase,
        returnOptimistic,
        GoalStatus.ACTIVE,
        showOnDashboard);
  }

  /** Rebuilds a goal from already-validated persisted state. */
  public static Goal reconstitute(
      UUID id,
      String name,
      String notes,
      BigDecimal targetAmount,
      LocalDate targetDate,
      BigDecimal plannedMonthlyContribution,
      BigDecimal returnPessimistic,
      BigDecimal returnBase,
      BigDecimal returnOptimistic,
      GoalStatus status,
      boolean showOnDashboard) {
    return new Goal(
        id,
        name,
        notes,
        targetAmount,
        targetDate,
        plannedMonthlyContribution,
        returnPessimistic,
        returnBase,
        returnOptimistic,
        status,
        showOnDashboard);
  }

  /**
   * Replaces every editable field (full replace). All-or-nothing: an invalid value leaves the goal
   * untouched. A resulting {@code ARCHIVED} status clears {@code showOnDashboard} whatever was
   * asked for; restoring (a non-archived status) uses the asked-for value, which is {@code false}
   * for a caller that just re-sends the archived goal's flag.
   */
  public void edit(
      String name,
      String notes,
      BigDecimal targetAmount,
      LocalDate targetDate,
      BigDecimal plannedMonthlyContribution,
      BigDecimal returnPessimistic,
      BigDecimal returnBase,
      BigDecimal returnOptimistic,
      GoalStatus status,
      boolean showOnDashboard) {
    Objects.requireNonNull(status, "status must not be null");
    apply(
        name,
        notes,
        targetAmount,
        targetDate,
        plannedMonthlyContribution,
        returnPessimistic,
        returnBase,
        returnOptimistic,
        status,
        showOnDashboard);
    this.status = status;
  }

  /** Archives the goal and clears {@code showOnDashboard}. */
  public void archive() {
    this.status = GoalStatus.ARCHIVED;
    this.showOnDashboard = false;
  }

  /** Turning it on for an archived goal throws; turning it off is always allowed. */
  public void setShowOnDashboard(boolean show) {
    if (show && status == GoalStatus.ARCHIVED) {
      throw new IllegalStateException("an archived goal cannot be shown on the dashboard");
    }
    this.showOnDashboard = show;
  }

  /** Validates everything first, then assigns, so a failed call changes nothing. */
  private void apply(
      String newName,
      String newNotes,
      BigDecimal newTargetAmount,
      LocalDate newTargetDate,
      BigDecimal newContribution,
      BigDecimal newPessimistic,
      BigDecimal newBase,
      BigDecimal newOptimistic,
      GoalStatus newStatus,
      boolean newShowOnDashboard) {
    String validName = requireValidName(newName);
    String validNotes = requireValidNotes(newNotes);
    BigDecimal validTarget = atScale(requirePositive(newTargetAmount, "targetAmount"));
    BigDecimal validContribution =
        atScale(requireNonNegative(newContribution, "plannedMonthlyContribution"));
    BigDecimal validPessimistic = atScale(requireReturn(newPessimistic, "returnPessimistic"));
    BigDecimal validBase = atScale(requireReturn(newBase, "returnBase"));
    BigDecimal validOptimistic = atScale(requireReturn(newOptimistic, "returnOptimistic"));
    if (validPessimistic.compareTo(validBase) > 0 || validBase.compareTo(validOptimistic) > 0) {
      throw new IllegalArgumentException(
          "returns must be ordered pessimistic <= base <= optimistic");
    }
    this.name = validName;
    this.notes = validNotes;
    this.targetAmount = validTarget;
    this.targetDate = newTargetDate;
    this.plannedMonthlyContribution = validContribution;
    this.returnPessimistic = validPessimistic;
    this.returnBase = validBase;
    this.returnOptimistic = validOptimistic;
    this.showOnDashboard = newStatus != GoalStatus.ARCHIVED && newShowOnDashboard;
  }

  private static String requireValidName(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (value.length() > TextFieldConstraints.MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "name must be at most " + TextFieldConstraints.MAX_NAME_LENGTH + " characters");
    }
    return value;
  }

  private static String requireValidNotes(String value) {
    if (value != null && value.length() > TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) {
      throw new IllegalArgumentException(
          "notes must be at most "
              + TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH
              + " characters");
    }
    return value;
  }

  private static BigDecimal requirePositive(BigDecimal value, String field) {
    requireNotNull(value, field);
    if (value.signum() <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return value;
  }

  private static BigDecimal requireNonNegative(BigDecimal value, String field) {
    requireNotNull(value, field);
    if (value.signum() < 0) {
      throw new IllegalArgumentException(field + " must not be negative");
    }
    return value;
  }

  private static BigDecimal requireReturn(BigDecimal value, String field) {
    requireNotNull(value, field);
    if (value.compareTo(MIN_RETURN) < 0 || value.compareTo(MAX_RETURN) > 0) {
      throw new IllegalArgumentException(
          field + " must be between " + MIN_RETURN + " and " + MAX_RETURN);
    }
    return value;
  }

  private static void requireNotNull(BigDecimal value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
  }

  /** Pads to two decimals, never rounds: more than two significant decimals is rejected. */
  private static BigDecimal atScale(BigDecimal value) {
    if (value.stripTrailingZeros().scale() > SCALE) {
      throw new IllegalArgumentException("at most " + SCALE + " decimal places are allowed");
    }
    return value.setScale(SCALE);
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getNotes() {
    return notes;
  }

  public BigDecimal getTargetAmount() {
    return targetAmount;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public BigDecimal getPlannedMonthlyContribution() {
    return plannedMonthlyContribution;
  }

  public BigDecimal getReturnPessimistic() {
    return returnPessimistic;
  }

  public BigDecimal getReturnBase() {
    return returnBase;
  }

  public BigDecimal getReturnOptimistic() {
    return returnOptimistic;
  }

  public GoalStatus getStatus() {
    return status;
  }

  public boolean isShowOnDashboard() {
    return showOnDashboard;
  }

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("name", name);
    snapshot.put("notes", notes);
    snapshot.put("targetAmount", targetAmount);
    snapshot.put("targetDate", targetDate == null ? null : targetDate.toString());
    snapshot.put("plannedMonthlyContribution", plannedMonthlyContribution);
    snapshot.put("returnPessimistic", returnPessimistic);
    snapshot.put("returnBase", returnBase);
    snapshot.put("returnOptimistic", returnOptimistic);
    snapshot.put("status", status.name());
    snapshot.put("showOnDashboard", showOnDashboard);
    return snapshot;
  }
}
