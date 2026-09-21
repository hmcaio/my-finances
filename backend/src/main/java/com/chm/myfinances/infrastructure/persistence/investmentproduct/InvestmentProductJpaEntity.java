package com.chm.myfinances.infrastructure.persistence.investmentproduct;

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
 * JPA mapping for the {@code investment_products} table (F008 spec). References to the account and
 * the taxonomy are plain id columns; the foreign keys (including the composite one tying the
 * sub-category to the category) live in the migration.
 */
@Entity
@Table(name = "investment_products")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentProductJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "investment_category_id", nullable = false)
  private UUID investmentCategoryId;

  @Column(name = "investment_subcategory_id")
  private UUID investmentSubcategoryId;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Column(name = "closed_date")
  private LocalDate closedDate;

  public InvestmentProductJpaEntity(
      UUID id,
      UUID accountId,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      LocalDate closedDate) {
    this.id = id;
    this.accountId = accountId;
    this.investmentCategoryId = investmentCategoryId;
    this.investmentSubcategoryId = investmentSubcategoryId;
    this.name = name;
    this.closedDate = closedDate;
  }
}
