package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeGoalAllocationRepository;
import com.chm.myfinances.testsupport.fakes.FakeGoalRepository;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalService} (F039, ADR 0034), written first (ADR 0004)
 * against hand-written fakes - plain JUnit, no Spring context.
 */
class GoalServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
  private static final LocalDate FUTURE = LocalDate.of(2028, 11, 5);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final GoalService service =
      new GoalService(
          goalRepository,
          allocationRepository,
          productRepository,
          new FakeIdGenerator(),
          clock,
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));

  private UUID productA;
  private UUID productB;

  @BeforeEach
  void setUp() {
    productA = saveProduct("Product A").getId();
    productB = saveProduct("Product B").getId();
  }

  private InvestmentProduct saveProduct(String name) {
    return productRepository.save(InvestmentProductMother.product().withName(name).build());
  }

  private static GoalInput input(LocalDate targetDate) {
    return new GoalInput(
        "Car",
        null,
        new BigDecimal("80000"),
        targetDate,
        new BigDecimal("2000"),
        new BigDecimal("2"),
        new BigDecimal("4"),
        new BigDecimal("6"),
        true);
  }

  private Goal saveGoal(GoalMother mother) {
    return goalRepository.save(mother.build());
  }

  private static AllocationInput alloc(UUID productId, String percentage) {
    return new AllocationInput(productId, new BigDecimal(percentage));
  }

  // --- create ---

  @Test
  void createStoresAnActiveGoalAndAuditsACreate() {
    Goal goal = service.create(input(FUTURE));

    assertThat(goalRepository.findById(goal.getId())).isPresent();
    assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    assertThat(goal.isShowOnDashboard()).isTrue();
    assertThat(auditLog.onlyEntry().entityType()).isEqualTo(AuditEntityType.GOAL);
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.CREATE);
    assertThat(auditLog.onlyEntry().entityId()).isEqualTo(goal.getId());
    assertThat(auditLog.onlyEntry().entityLabel()).isEqualTo("Car");
  }

  @Test
  void createTakesTheIdFromTheGenerator() {
    UUID nextId = UUID.randomUUID();
    GoalService seeded =
        new GoalService(
            goalRepository,
            allocationRepository,
            productRepository,
            new FakeIdGenerator(nextId),
            clock,
            new AuditRecorder(auditLog, AuditReferenceLabels.none()));

    assertThat(seeded.create(input(FUTURE)).getId()).isEqualTo(nextId);
  }

  @Test
  void createAcceptsADatelessGoal() {
    assertThat(service.create(input(null)).getTargetDate()).isNull();
  }

  @Test
  void createRejectsATargetDateThatIsTodayOrPast() {
    assertThatThrownBy(() -> service.create(input(TODAY))).isInstanceOf(InvalidGoalException.class);
    assertThatThrownBy(() -> service.create(input(TODAY.minusDays(1))))
        .isInstanceOf(InvalidGoalException.class);
    assertThat(goalRepository.findAll()).isEmpty();
    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void createAcceptsTomorrowAsTheEarliestTargetDate() {
    assertThat(service.create(input(TODAY.plusDays(1))).getTargetDate())
        .isEqualTo(TODAY.plusDays(1));
  }

  @Test
  void createTranslatesADomainViolationToInvalidGoal() {
    GoalInput blank =
        new GoalInput(
            " ",
            null,
            BigDecimal.TEN,
            FUTURE,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            true);

    assertThatThrownBy(() -> service.create(blank)).isInstanceOf(InvalidGoalException.class);
  }

  // --- edit ---

  @Test
  void editReplacesTheFieldsAndAuditsAnUpdate() {
    Goal goal = service.create(input(FUTURE));
    auditLog.entries().clear();

    GoalInput changed =
        new GoalInput(
            "House",
            "n",
            new BigDecimal("500000"),
            FUTURE.plusYears(1),
            new BigDecimal("3000"),
            new BigDecimal("1"),
            new BigDecimal("2"),
            new BigDecimal("3"),
            true);
    Goal edited = service.edit(goal.getId(), changed, GoalStatus.ACHIEVED);

    assertThat(edited.getName()).isEqualTo("House");
    assertThat(edited.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.UPDATE);
    assertThat(auditLog.onlyEntry().changes()).containsKeys("name", "targetAmount", "status");
  }

  @Test
  void editWithNothingChangedAuditsNothing() {
    Goal goal = service.create(input(FUTURE));
    auditLog.entries().clear();

    service.edit(goal.getId(), input(FUTURE), GoalStatus.ACTIVE);

    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void editOfAnUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> service.edit(UUID.randomUUID(), input(FUTURE), GoalStatus.ACTIVE))
        .isInstanceOf(GoalNotFoundException.class);
  }

  @Test
  void editRejectsAChangedTargetDateThatIsNotInTheFuture() {
    Goal goal = service.create(input(FUTURE));

    assertThatThrownBy(() -> service.edit(goal.getId(), input(TODAY), GoalStatus.ACTIVE))
        .isInstanceOf(InvalidGoalException.class);
    assertThat(goalRepository.findById(goal.getId()).orElseThrow().getTargetDate())
        .isEqualTo(FUTURE);
  }

  @Test
  void editAcceptsAnUnchangedTargetDateThatHasSincePassed() {
    LocalDate passed = TODAY.minusMonths(2);
    Goal goal = saveGoal(GoalMother.goal().withTargetDate(passed));

    Goal edited =
        service.edit(
            goal.getId(),
            new GoalInput(
                "Car",
                "edited notes",
                new BigDecimal("80000"),
                passed,
                new BigDecimal("2000"),
                new BigDecimal("2"),
                new BigDecimal("4"),
                new BigDecimal("6"),
                true),
            GoalStatus.ACTIVE);

    assertThat(edited.getNotes()).isEqualTo("edited notes");
    assertThat(edited.getTargetDate()).isEqualTo(passed);
  }

  @Test
  void editToArchivedClearsTheDashboardFlag() {
    Goal goal = service.create(input(FUTURE));

    Goal archived = service.edit(goal.getId(), input(FUTURE), GoalStatus.ARCHIVED);

    assertThat(archived.getStatus()).isEqualTo(GoalStatus.ARCHIVED);
    assertThat(archived.isShowOnDashboard()).isFalse();
  }

  @Test
  void archivingFreesThePercentagesOfItsProducts() {
    Goal first = service.create(input(FUTURE));
    Goal second = service.create(input(FUTURE));
    service.replaceAllocations(first.getId(), List.of(alloc(productA, "70")));

    service.edit(first.getId(), input(FUTURE), GoalStatus.ARCHIVED);

    assertThat(service.replaceAllocations(second.getId(), List.of(alloc(productA, "100"))))
        .hasSize(1);
  }

  @Test
  void restoringKeepsTheDashboardFlagOff() {
    Goal goal = service.create(input(FUTURE));
    service.edit(goal.getId(), input(FUTURE), GoalStatus.ARCHIVED);
    GoalInput restoredInput =
        new GoalInput(
            "Car",
            null,
            new BigDecimal("80000"),
            FUTURE,
            new BigDecimal("2000"),
            new BigDecimal("2"),
            new BigDecimal("4"),
            new BigDecimal("6"),
            false);

    Goal restored = service.edit(goal.getId(), restoredInput, GoalStatus.ACTIVE);

    assertThat(restored.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    assertThat(restored.isShowOnDashboard()).isFalse();
  }

  @Test
  void restoringRechecksThePercentagesAndRejectsWhenTheyNoLongerFit() {
    Goal first = service.create(input(FUTURE));
    Goal second = service.create(input(FUTURE));
    service.replaceAllocations(first.getId(), List.of(alloc(productA, "70")));
    service.edit(first.getId(), input(FUTURE), GoalStatus.ARCHIVED);
    service.replaceAllocations(second.getId(), List.of(alloc(productA, "50")));

    assertThatThrownBy(() -> service.edit(first.getId(), input(FUTURE), GoalStatus.ACTIVE))
        .isInstanceOf(GoalAllocationExceedsProductException.class);
    assertThat(goalRepository.findById(first.getId()).orElseThrow().getStatus())
        .isEqualTo(GoalStatus.ARCHIVED);
  }

  @Test
  void restoringSucceedsWhenThePercentagesStillFit() {
    Goal first = service.create(input(FUTURE));
    Goal second = service.create(input(FUTURE));
    service.replaceAllocations(first.getId(), List.of(alloc(productA, "70")));
    service.edit(first.getId(), input(FUTURE), GoalStatus.ARCHIVED);
    service.replaceAllocations(second.getId(), List.of(alloc(productA, "30")));

    Goal restored = service.edit(first.getId(), input(FUTURE), GoalStatus.ACTIVE);

    assertThat(restored.getStatus()).isEqualTo(GoalStatus.ACTIVE);
  }

  // --- show on dashboard ---

  @Test
  void setShowOnDashboardTogglesTheFlagAndAudits() {
    Goal goal = service.create(input(FUTURE));
    auditLog.entries().clear();

    Goal off = service.setShowOnDashboard(goal.getId(), false);

    assertThat(off.isShowOnDashboard()).isFalse();
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.UPDATE);
    assertThat(auditLog.onlyEntry().changes()).containsOnlyKeys("showOnDashboard");
  }

  @Test
  void turningShowOnDashboardOnForAnArchivedGoalIsRejected() {
    Goal goal = service.create(input(FUTURE));
    service.edit(goal.getId(), input(FUTURE), GoalStatus.ARCHIVED);

    assertThatThrownBy(() -> service.setShowOnDashboard(goal.getId(), true))
        .isInstanceOf(GoalArchivedException.class);
  }

  @Test
  void turningShowOnDashboardOffForAnArchivedGoalIsAllowed() {
    Goal goal = service.create(input(FUTURE));
    service.edit(goal.getId(), input(FUTURE), GoalStatus.ARCHIVED);

    assertThat(service.setShowOnDashboard(goal.getId(), false).isShowOnDashboard()).isFalse();
  }

  @Test
  void setShowOnDashboardOfAnUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> service.setShowOnDashboard(UUID.randomUUID(), true))
        .isInstanceOf(GoalNotFoundException.class);
  }

  // --- delete ---

  @Test
  void deleteRemovesTheGoalAndAuditsADeleteWithItsAllocations() {
    Goal goal = service.create(input(FUTURE));
    service.replaceAllocations(goal.getId(), List.of(alloc(productA, "40")));
    auditLog.entries().clear();

    service.delete(goal.getId());

    assertThat(goalRepository.findById(goal.getId())).isEmpty();
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.DELETE);
    assertThat(auditLog.onlyEntry().changes()).containsKey("allocations");
  }

  @Test
  void deleteOfAnUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(GoalNotFoundException.class);
  }

  // --- replace allocations ---

  @Test
  void replaceAllocationsStoresTheSetAndAuditsAnUpdateOfTheGoal() {
    Goal goal = service.create(input(FUTURE));
    auditLog.entries().clear();

    List<GoalAllocation> stored =
        service.replaceAllocations(
            goal.getId(), List.of(alloc(productA, "60"), alloc(productB, "10.5")));

    assertThat(stored).hasSize(2);
    assertThat(allocationRepository.findByGoalId(goal.getId())).hasSize(2);
    assertThat(auditLog.onlyEntry().entityType()).isEqualTo(AuditEntityType.GOAL);
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.UPDATE);
    assertThat(auditLog.onlyEntry().entityId()).isEqualTo(goal.getId());
    assertThat(auditLog.onlyEntry().changes()).containsOnlyKeys("allocations");
  }

  @Test
  void replaceAllocationsReplacesThePreviousSetAndAnEmptyListClearsIt() {
    Goal goal = service.create(input(FUTURE));
    service.replaceAllocations(goal.getId(), List.of(alloc(productA, "60")));

    service.replaceAllocations(goal.getId(), List.of(alloc(productB, "20")));
    assertThat(allocationRepository.findByGoalId(goal.getId()))
        .extracting(GoalAllocation::getProductId)
        .containsExactly(productB);

    service.replaceAllocations(goal.getId(), List.of());
    assertThat(allocationRepository.findByGoalId(goal.getId())).isEmpty();
  }

  @Test
  void savingTheSameAllocationsAgainAuditsNothing() {
    Goal goal = service.create(input(FUTURE));
    service.replaceAllocations(goal.getId(), List.of(alloc(productA, "60")));
    auditLog.entries().clear();

    service.replaceAllocations(goal.getId(), List.of(alloc(productA, "60.00")));

    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void replaceAllocationsRejectsADuplicateProduct() {
    Goal goal = service.create(input(FUTURE));

    assertThatThrownBy(
            () ->
                service.replaceAllocations(
                    goal.getId(), List.of(alloc(productA, "10"), alloc(productA, "20"))))
        .isInstanceOf(InvalidGoalException.class);
    assertThat(allocationRepository.findByGoalId(goal.getId())).isEmpty();
  }

  @Test
  void replaceAllocationsRejectsAnInvalidPercentage() {
    Goal goal = service.create(input(FUTURE));

    assertThatThrownBy(
            () -> service.replaceAllocations(goal.getId(), List.of(alloc(productA, "0"))))
        .isInstanceOf(InvalidGoalException.class);
    assertThatThrownBy(
            () -> service.replaceAllocations(goal.getId(), List.of(alloc(productA, "100.01"))))
        .isInstanceOf(InvalidGoalException.class);
  }

  @Test
  void replaceAllocationsRejectsAnUnknownProductAsNotFound() {
    Goal goal = service.create(input(FUTURE));

    assertThatThrownBy(
            () -> service.replaceAllocations(goal.getId(), List.of(alloc(UUID.randomUUID(), "10"))))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void replaceAllocationsRejectsAnUnknownGoalAsNotFound() {
    assertThatThrownBy(
            () -> service.replaceAllocations(UUID.randomUUID(), List.of(alloc(productA, "10"))))
        .isInstanceOf(GoalNotFoundException.class);
  }

  @Test
  void replaceAllocationsRejectsAnArchivedGoal() {
    Goal goal = service.create(input(FUTURE));
    service.edit(goal.getId(), input(FUTURE), GoalStatus.ARCHIVED);

    assertThatThrownBy(
            () -> service.replaceAllocations(goal.getId(), List.of(alloc(productA, "10"))))
        .isInstanceOf(GoalArchivedException.class);
  }

  @Test
  void replaceAllocationsRejectsAProductOverOneHundredAcrossOtherGoals() {
    Goal first = service.create(input(FUTURE));
    Goal second = service.create(input(FUTURE));
    Goal third = service.create(input(FUTURE));
    service.replaceAllocations(first.getId(), List.of(alloc(productA, "60")));
    service.replaceAllocations(second.getId(), List.of(alloc(productA, "40")));

    assertThatThrownBy(
            () -> service.replaceAllocations(third.getId(), List.of(alloc(productA, "10"))))
        .isInstanceOf(GoalAllocationExceedsProductException.class);
    assertThat(allocationRepository.findByGoalId(third.getId())).isEmpty();
  }

  @Test
  void aGoalMayRaiseItsOwnShareUpToWhatOthersLeave() {
    Goal first = service.create(input(FUTURE));
    Goal second = service.create(input(FUTURE));
    service.replaceAllocations(first.getId(), List.of(alloc(productA, "60")));
    service.replaceAllocations(second.getId(), List.of(alloc(productA, "30")));

    service.replaceAllocations(second.getId(), List.of(alloc(productA, "40")));

    assertThat(allocationRepository.findByGoalId(second.getId()).get(0).getPercentage())
        .isEqualByComparingTo("40");
    assertThatThrownBy(
            () -> service.replaceAllocations(second.getId(), List.of(alloc(productA, "40.01"))))
        .isInstanceOf(GoalAllocationExceedsProductException.class);
  }

  @Test
  void findByIdOfAnUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(GoalNotFoundException.class);
  }
}
