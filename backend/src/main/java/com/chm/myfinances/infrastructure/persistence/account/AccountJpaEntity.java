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

/** JPA mapping for the {@code accounts} table (F003 spec). */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class AccountJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Column(length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String institution;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountType type;

  @Column(name = "opening_balance", nullable = false)
  private BigDecimal openingBalance;

  @Column(name = "opening_balance_date", nullable = false)
  private LocalDate openingBalanceDate;

  @Column(name = "closed_date")
  private LocalDate closedDate;

  public AccountJpaEntity(
      UUID id,
      String name,
      String institution,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate,
      LocalDate closedDate) {
    this.id = id;
    this.name = name;
    this.institution = institution;
    this.type = type;
    this.openingBalance = openingBalance;
    this.openingBalanceDate = openingBalanceDate;
    this.closedDate = closedDate;
  }
}
