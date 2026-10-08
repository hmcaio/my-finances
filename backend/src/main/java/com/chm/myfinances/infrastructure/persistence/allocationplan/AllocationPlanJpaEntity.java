package com.chm.myfinances.infrastructure.persistence.allocationplan;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA mapping for the {@code allocation_plans} table (F026 spec): the singleton marker row, no
 * other columns.
 */
@Entity
@Table(name = "allocation_plans")
@Getter
@NoArgsConstructor
public class AllocationPlanJpaEntity extends AuditableEntity {

  @Id private UUID id;

  public AllocationPlanJpaEntity(UUID id) {
    this.id = id;
  }
}
