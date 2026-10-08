package com.chm.myfinances.infrastructure.persistence.allocationplan;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code allocation_plan_versions} table (F026 spec). {@code effectiveFrom}
 * is stored as a {@code date} - always the first day of the month, same convention as {@code
 * BudgetVersionJpaEntity} - converted to/from the domain's {@code YearMonth} only at the adapter
 * boundary.
 */
@Entity
@Table(name = "allocation_plan_versions")
@Getter
@Setter
@NoArgsConstructor
public class AllocationPlanVersionJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  public AllocationPlanVersionJpaEntity(UUID id, UUID planId, LocalDate effectiveFrom) {
    this.id = id;
    this.planId = planId;
    this.effectiveFrom = effectiveFrom;
  }
}
