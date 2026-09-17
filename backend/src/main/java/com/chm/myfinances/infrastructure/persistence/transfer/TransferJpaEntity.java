package com.chm.myfinances.infrastructure.persistence.transfer;

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
 * JPA mapping for the {@code transfers} table (F005 spec). {@code fromAccountId}/{@code
 * toAccountId} are plain {@code UUID} columns, not JPA associations - same standalone-aggregate
 * style as {@code TransactionJpaEntity}.
 */
@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
public class TransferJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private LocalDate date;

  @Column(name = "from_account_id", nullable = false)
  private UUID fromAccountId;

  @Column(name = "to_account_id", nullable = false)
  private UUID toAccountId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String description;

  @Column(name = "additional_notes")
  private String additionalNotes;

  public TransferJpaEntity(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    this.id = id;
    this.date = date;
    this.fromAccountId = fromAccountId;
    this.toAccountId = toAccountId;
    this.amount = amount;
    this.description = description;
    this.additionalNotes = additionalNotes;
  }
}
