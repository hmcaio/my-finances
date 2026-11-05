package com.chm.myfinances.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Goal} (F039, ADR 0034). Pure JUnit, written before the class
 * itself (ADR 0004).
 */
class GoalTest {

  private static final LocalDate DATE = LocalDate.of(2028, 11, 5);

  private static Goal goal() {
    return create("Car", "notes", "80000.00", "2000.00", "2", "4", "6");
  }

  private static Goal create(
      String name,
      String notes,
      String target,
      String contribution,
      String pessimistic,
      String base,
      String optimistic) {
    return Goal.create(
        UUID.randomUUID(),
        name,
        notes,
        new BigDecimal(target),
        DATE,
        new BigDecimal(contribution),
        new BigDecimal(pessimistic),
        new BigDecimal(base),
        new BigDecimal(optimistic),
        true);
  }

  private static void edit(
      Goal goal, String target, String contribution, String p, String b, String o) {
    goal.edit(
        "Car",
        null,
        new BigDecimal(target),
        DATE,
        new BigDecimal(contribution),
        new BigDecimal(p),
        new BigDecimal(b),
        new BigDecimal(o),
        GoalStatus.ACTIVE,
        true);
  }

  @Test
  void createsAnActiveGoalWithGivenFields() {
    UUID id = UUID.randomUUID();

    Goal goal =
        Goal.create(
            id,
            "Car",
            "a note",
            new BigDecimal("80000"),
            DATE,
            new BigDecimal("2000"),
            new BigDecimal("2"),
            new BigDecimal("4"),
            new BigDecimal("6"),
            true);

    assertThat(goal.getId()).isEqualTo(id);
    assertThat(goal.getName()).isEqualTo("Car");
    assertThat(goal.getNotes()).isEqualTo("a note");
    assertThat(goal.getTargetAmount()).isEqualByComparingTo("80000");
    assertThat(goal.getTargetDate()).isEqualTo(DATE);
    assertThat(goal.getPlannedMonthlyContribution()).isEqualByComparingTo("2000");
    assertThat(goal.getReturnPessimistic()).isEqualByComparingTo("2");
    assertThat(goal.getReturnBase()).isEqualByComparingTo("4");
    assertThat(goal.getReturnOptimistic()).isEqualByComparingTo("6");
    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    assertThat(goal.isShowOnDashboard()).isTrue();
  }

  @Test
  void aDatelessGoalWithNoNotesAndZeroContributionIsValid() {
    Goal goal =
        Goal.create(
            UUID.randomUUID(),
            "Emergency fund",
            null,
            new BigDecimal("30000"),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false);

    assertThat(goal.getTargetDate()).isNull();
    assertThat(goal.getNotes()).isNull();
    assertThat(goal.getPlannedMonthlyContribution()).isEqualByComparingTo("0");
  }

  @Test
  void createRejectsABlankOrNullName() {
    assertThatThrownBy(() -> create(" ", null, "1", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(null, null, "1", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsANameOverTheLimitAndAcceptsOneAtIt() {
    String atLimit = "n".repeat(TextFieldConstraints.MAX_NAME_LENGTH);

    assertThat(create(atLimit, null, "1", "0", "1", "1", "1").getName()).isEqualTo(atLimit);
    assertThatThrownBy(() -> create(atLimit + "n", null, "1", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNotesOverTheLimitAndAcceptsThemAtIt() {
    String atLimit = "n".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH);

    assertThat(create("Car", atLimit, "1", "0", "1", "1", "1").getNotes()).isEqualTo(atLimit);
    assertThatThrownBy(() -> create("Car", atLimit + "n", "1", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAZeroOrNegativeTarget() {
    assertThatThrownBy(() -> create("Car", null, "0", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create("Car", null, "-1", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsANegativeContribution() {
    assertThatThrownBy(() -> create("Car", null, "1", "-0.01", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsAReturnOutsideTheRange() {
    assertThatThrownBy(() -> create("Car", null, "1", "0", "-50.01", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create("Car", null, "1", "0", "-50", "1", "100.01"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create("Car", null, "1", "0", "1", "100.01", "100.01"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsReturnsAtTheBounds() {
    Goal goal = create("Car", null, "1", "0", "-50", "0", "100");

    assertThat(goal.getReturnPessimistic()).isEqualByComparingTo("-50");
    assertThat(goal.getReturnOptimistic()).isEqualByComparingTo("100");
  }

  @Test
  void createRejectsUnorderedReturns() {
    assertThatThrownBy(() -> create("Car", null, "1", "0", "5", "4", "6"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create("Car", null, "1", "0", "2", "7", "6"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsEqualReturns() {
    assertThat(create("Car", null, "1", "0", "4", "4", "4").getReturnBase())
        .isEqualByComparingTo("4");
  }

  @Test
  void createRejectsMoneyAndPercentagesWithTooManyDecimals() {
    assertThatThrownBy(() -> create("Car", null, "1.005", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create("Car", null, "1", "0.001", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create("Car", null, "1", "0", "1.001", "2", "3"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createPadsMoneyAndPercentagesToTheColumnScale() {
    Goal goal = create("Car", null, "80000", "2000", "2", "4", "6");

    assertThat(goal.getTargetAmount()).isEqualTo(new BigDecimal("80000.00"));
    assertThat(goal.getPlannedMonthlyContribution()).isEqualTo(new BigDecimal("2000.00"));
    assertThat(goal.getReturnBase()).isEqualTo(new BigDecimal("4.00"));
  }

  @Test
  void createRejectsNullRequiredFields() {
    assertThatThrownBy(
            () ->
                Goal.create(
                    null,
                    "Car",
                    null,
                    BigDecimal.ONE,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    true))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                Goal.create(
                    UUID.randomUUID(),
                    "Car",
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void archiveSetsTheStatusAndClearsShowOnDashboard() {
    Goal goal = goal();

    goal.archive();

    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ARCHIVED);
    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void showOnDashboardCannotBeTurnedOnForAnArchivedGoal() {
    Goal goal = goal();
    goal.archive();

    assertThatThrownBy(() -> goal.setShowOnDashboard(true))
        .isInstanceOf(IllegalStateException.class);
    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void showOnDashboardCanBeToggledOnAnUnarchivedGoal() {
    Goal goal = goal();

    goal.setShowOnDashboard(false);
    assertThat(goal.isShowOnDashboard()).isFalse();
    goal.setShowOnDashboard(true);
    assertThat(goal.isShowOnDashboard()).isTrue();
  }

  @Test
  void turningShowOnDashboardOffForAnArchivedGoalIsAllowed() {
    Goal goal = goal();
    goal.archive();

    goal.setShowOnDashboard(false);

    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void editReplacesEveryField() {
    Goal goal = goal();

    goal.edit(
        "House",
        "n",
        new BigDecimal("500000"),
        null,
        BigDecimal.ZERO,
        new BigDecimal("1"),
        new BigDecimal("2"),
        new BigDecimal("3"),
        GoalStatus.ACHIEVED,
        false);

    assertThat(goal.getName()).isEqualTo("House");
    assertThat(goal.getNotes()).isEqualTo("n");
    assertThat(goal.getTargetAmount()).isEqualByComparingTo("500000");
    assertThat(goal.getTargetDate()).isNull();
    assertThat(goal.getPlannedMonthlyContribution()).isEqualByComparingTo("0");
    assertThat(goal.getReturnOptimistic()).isEqualByComparingTo("3");
    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void editToArchivedClearsShowOnDashboardEvenWhenRequested() {
    Goal goal = goal();

    goal.edit(
        "Car",
        null,
        BigDecimal.TEN,
        DATE,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        GoalStatus.ARCHIVED,
        true);

    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ARCHIVED);
    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void restoringAnArchivedGoalKeepsShowOnDashboardOff() {
    Goal goal = goal();
    goal.archive();

    goal.edit(
        "Car",
        null,
        BigDecimal.TEN,
        DATE,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        GoalStatus.ACTIVE,
        false);

    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void anInvalidEditLeavesTheGoalUntouched() {
    Goal goal = goal();

    assertThatThrownBy(() -> edit(goal, "0", "0", "1", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> edit(goal, "10", "0", "9", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(goal.getTargetAmount()).isEqualByComparingTo("80000");
    assertThat(goal.getReturnPessimistic()).isEqualByComparingTo("2");
  }

  @Test
  void reconstitutePreservesTheStoredStateWithoutTheArchivedInvariantBeingBypassed() {
    Goal goal =
        Goal.reconstitute(
            UUID.randomUUID(),
            "Car",
            null,
            new BigDecimal("80000.00"),
            DATE,
            new BigDecimal("0.00"),
            new BigDecimal("2.00"),
            new BigDecimal("4.00"),
            new BigDecimal("6.00"),
            GoalStatus.ARCHIVED,
            false);

    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ARCHIVED);
    assertThat(goal.isShowOnDashboard()).isFalse();
  }

  @Test
  void auditSnapshotCarriesEveryPersistedField() {
    Goal goal = goal();

    assertThat(goal.toAuditSnapshot().keySet())
        .containsExactlyInAnyOrder(
            "name",
            "notes",
            "targetAmount",
            "targetDate",
            "plannedMonthlyContribution",
            "returnPessimistic",
            "returnBase",
            "returnOptimistic",
            "status",
            "showOnDashboard");
    assertThat(goal.toAuditSnapshot()).containsEntry("targetDate", DATE.toString());
    assertThat(goal.toAuditSnapshot()).containsEntry("status", "ACTIVE");
  }
}
