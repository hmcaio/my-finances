package com.chm.myfinances.infrastructure.persistence.investmentholding;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
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
 * JPA mapping for the {@code investment_holdings} table (F022 spec, ADR 0020). References to the
 * product and the account are plain id columns, both immutable ({@code updatable = false}) since
 * re-pointing either would misattribute existing snapshots/trades.
 */
@Entity
@Table(name = "investment_holdings")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentHoldingJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "closed_date")
  private LocalDate closedDate;

  @Column(name = "additional_notes", length = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH)
  private String additionalNotes;

  public InvestmentHoldingJpaEntity(
      UUID id, UUID productId, UUID accountId, LocalDate closedDate, String additionalNotes) {
    this.id = id;
    this.productId = productId;
    this.accountId = accountId;
    this.closedDate = closedDate;
    this.additionalNotes = additionalNotes;
  }
}
