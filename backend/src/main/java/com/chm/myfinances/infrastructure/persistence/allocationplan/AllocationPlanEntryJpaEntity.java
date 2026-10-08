package com.chm.myfinances.infrastructure.persistence.allocationplan;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code allocation_plan_entries} table (F026 spec): one line of an {@code
 * AllocationPlanVersion}. A plain {@code versionId} column, not a JPA association - same
 * standalone-aggregate style as every other entity in this codebase; {@link
 * AllocationPlanVersionRepositoryAdapter} loads/replaces a version's entries explicitly rather than
 * relying on a JPA collection mapping.
 */
@Entity
@Table(name = "allocation_plan_entries")
@Getter
@Setter
@NoArgsConstructor
public class AllocationPlanEntryJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Column(name = "investment_product_id", nullable = false)
  private UUID investmentProductId;

  @Column(name = "target_percentage", nullable = false)
  private BigDecimal targetPercentage;

  public AllocationPlanEntryJpaEntity(
      UUID id, UUID versionId, UUID investmentProductId, BigDecimal targetPercentage) {
    this.id = id;
    this.versionId = versionId;
    this.investmentProductId = investmentProductId;
    this.targetPercentage = targetPercentage;
  }
}
