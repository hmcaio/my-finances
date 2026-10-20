package com.chm.myfinances.infrastructure.persistence.budget;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code budgets} table (F006 spec). {@code categoryId} is a plain {@code UUID}
 * column, not a JPA association - same standalone-aggregate style as {@code
 * TransactionJpaEntity}/{@code TransferJpaEntity}.
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
public class BudgetJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "category_id", nullable = false, unique = true)
  private UUID categoryId;

  public BudgetJpaEntity(UUID id, UUID categoryId) {
    this.id = id;
    this.categoryId = categoryId;
  }
}
