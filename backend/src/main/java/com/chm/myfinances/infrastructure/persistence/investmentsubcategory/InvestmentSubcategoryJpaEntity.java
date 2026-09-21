package com.chm.myfinances.infrastructure.persistence.investmentsubcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
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
 * JPA mapping for the {@code investment_subcategories} table (F008 spec). The parent is a plain id
 * column, not a {@code @ManyToOne}: the aggregates reference each other by id only.
 */
@Entity
@Table(name = "investment_subcategories")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentSubcategoryJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "investment_category_id", nullable = false, updatable = false)
  private UUID investmentCategoryId;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  public InvestmentSubcategoryJpaEntity(UUID id, UUID investmentCategoryId, String name) {
    this.id = id;
    this.investmentCategoryId = investmentCategoryId;
    this.name = name;
  }
}
