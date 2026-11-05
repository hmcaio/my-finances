package com.chm.myfinances.infrastructure.persistence.goal;

import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA mapping for the {@code goals} table (F039 spec, ADR 0034). */
@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
public class GoalJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Column(length = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH)
  private String notes;

  @Column(name = "target_amount", nullable = false)
  private BigDecimal targetAmount;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(name = "planned_monthly_contribution", nullable = false)
  private BigDecimal plannedMonthlyContribution;

  @Column(name = "return_pessimistic", nullable = false)
  private BigDecimal returnPessimistic;

  @Column(name = "return_base", nullable = false)
  private BigDecimal returnBase;

  @Column(name = "return_optimistic", nullable = false)
  private BigDecimal returnOptimistic;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GoalStatus status;

  @Column(name = "show_on_dashboard", nullable = false)
  private boolean showOnDashboard;

  public GoalJpaEntity(
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
    this.id = id;
    this.name = name;
    this.notes = notes;
    this.targetAmount = targetAmount;
    this.targetDate = targetDate;
    this.plannedMonthlyContribution = plannedMonthlyContribution;
    this.returnPessimistic = returnPessimistic;
    this.returnBase = returnBase;
    this.returnOptimistic = returnOptimistic;
    this.status = status;
    this.showOnDashboard = showOnDashboard;
  }
}
