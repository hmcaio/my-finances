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
 * JPA mapping for the {@code investment_snapshots} table, rekeyed by holding (F022 spec, ADR 0020).
 * {@code holdingId} is a plain {@code UUID} column, not a JPA association - same
 * standalone-aggregate style as the other entities.
 */
@Entity
@Table(name = "investment_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentSnapshotJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "holding_id", nullable = false, updatable = false)
  private UUID holdingId;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private BigDecimal balance;

  public InvestmentSnapshotJpaEntity(UUID id, UUID holdingId, LocalDate date, BigDecimal balance) {
    this.id = id;
    this.holdingId = holdingId;
    this.date = date;
    this.balance = balance;
  }
}
