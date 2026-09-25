package com.chm.myfinances.infrastructure.persistence.investmentsnapshot;

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
 * JPA mapping for the {@code investment_snapshots} table (F009 spec). {@code productId} is a plain
 * {@code UUID} column, not a JPA association - same standalone-aggregate style as the other
 * entities.
 */
@Entity
@Table(name = "investment_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentSnapshotJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private BigDecimal balance;

  public InvestmentSnapshotJpaEntity(UUID id, UUID productId, LocalDate date, BigDecimal balance) {
    this.id = id;
    this.productId = productId;
    this.date = date;
    this.balance = balance;
  }
}
