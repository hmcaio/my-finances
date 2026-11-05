package com.chm.myfinances.infrastructure.persistence.goal;

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
 * JPA mapping for the {@code goal_allocations} table (F039 spec, ADR 0034). The goal and the
 * product are plain id columns, not JPA associations - the same standalone-aggregate style as
 * {@code InvestmentOpeningPositionJpaEntity}.
 */
@Entity
@Table(name = "goal_allocations")
@Getter
@Setter
@NoArgsConstructor
public class GoalAllocationJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "goal_id", nullable = false, updatable = false)
  private UUID goalId;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false)
  private BigDecimal percentage;

  public GoalAllocationJpaEntity(UUID id, UUID goalId, UUID productId, BigDecimal percentage) {
    this.id = id;
    this.goalId = goalId;
    this.productId = productId;
    this.percentage = percentage;
  }
}
