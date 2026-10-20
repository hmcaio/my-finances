package com.chm.myfinances.infrastructure.persistence.budget;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code budget_versions} table (F006 spec). {@code effectiveFrom} is stored as
 * a {@code date} - always the first day of the month - the domain's {@code YearMonth} is converted
 * to/from that convention only at {@code BudgetVersionRepositoryAdapter} (ADR 0004: the domain
 * layer stays free of persistence-representation detail).
 */
@Entity
@Table(name = "budget_versions")
@Getter
@Setter
@NoArgsConstructor
public class BudgetVersionJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "budget_id", nullable = false)
  private UUID budgetId;

  /** {@code null} on a tombstone version ("no budget from this month", issue #61, V16). */
  @Column(name = "monthly_cap")
  private BigDecimal monthlyCap;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  public BudgetVersionJpaEntity(
      UUID id, UUID budgetId, BigDecimal monthlyCap, LocalDate effectiveFrom) {
    this.id = id;
    this.budgetId = budgetId;
    this.monthlyCap = monthlyCap;
    this.effectiveFrom = effectiveFrom;
  }
}
