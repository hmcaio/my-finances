package com.chm.myfinances.infrastructure.persistence.transfer;

import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code transfer_trade_lines} table (F027 spec, ADR 0024): one line of a
 * {@code TradeConfirmation}. A plain {@code transferId} column, not a JPA association - same
 * standalone-aggregate style as {@code AllocationPlanEntryJpaEntity}; {@link
 * TransferRepositoryAdapter} loads/replaces a transfer's lines explicitly rather than relying on a
 * JPA collection mapping.
 */
@Entity
@Table(name = "transfer_trade_lines")
@Getter
@Setter
@NoArgsConstructor
public class TransferTradeLineJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "transfer_id", nullable = false)
  private UUID transferId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 4)
  private TradeSide side;

  @Column(nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  @Column(name = "resulting_balance")
  private BigDecimal resultingBalance;

  @Column(name = "close_holding", nullable = false)
  private boolean closeHolding;

  public TransferTradeLineJpaEntity(
      UUID id,
      UUID transferId,
      UUID productId,
      TradeSide side,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal resultingBalance,
      boolean closeHolding) {
    this.id = id;
    this.transferId = transferId;
    this.productId = productId;
    this.side = side;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.resultingBalance = resultingBalance;
    this.closeHolding = closeHolding;
  }
}
