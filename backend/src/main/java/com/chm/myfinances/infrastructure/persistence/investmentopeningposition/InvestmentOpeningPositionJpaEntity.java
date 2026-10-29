package com.chm.myfinances.infrastructure.persistence.investmentopeningposition;

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
 * JPA mapping for the {@code investment_opening_positions} table (F038 spec, ADR 0033). {@code
 * holdingId} is a plain {@code UUID} column, not a JPA association - same standalone-aggregate
 * style as {@code InvestmentSnapshotJpaEntity}.
 */
@Entity
@Table(name = "investment_opening_positions")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentOpeningPositionJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "holding_id", nullable = false, updatable = false)
  private UUID holdingId;

  @Column(name = "as_of_date", nullable = false)
  private LocalDate asOfDate;

  @Column(nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  public InvestmentOpeningPositionJpaEntity(
      UUID id, UUID holdingId, LocalDate asOfDate, BigDecimal quantity, BigDecimal unitPrice) {
    this.id = id;
    this.holdingId = holdingId;
    this.asOfDate = asOfDate;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }
}
