package com.chm.myfinances.infrastructure.persistence.account;

import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code accounts} table (F003 spec). The opening columns are nullable: they
 * are null exactly for an {@code INVESTMENT} account (F008, backed by a DB CHECK).
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class AccountJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Column(name = "institution_id", nullable = false)
  private UUID institutionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountType type;

  @Column(name = "opening_balance")
  private BigDecimal openingBalance;

  @Column(name = "opening_balance_date")
  private LocalDate openingBalanceDate;

  @Column(name = "closed_date")
  private LocalDate closedDate;

  public AccountJpaEntity(
      UUID id,
      String name,
      UUID institutionId,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate,
      LocalDate closedDate) {
    this.id = id;
    this.name = name;
    this.institutionId = institutionId;
    this.type = type;
    this.openingBalance = openingBalance;
    this.openingBalanceDate = openingBalanceDate;
    this.closedDate = closedDate;
  }
}
