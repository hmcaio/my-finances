package com.chm.myfinances.infrastructure.persistence.investmentproduct;

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
 * JPA mapping for the {@code investment_products} table (F022 spec, ADR 0020): pure taxonomy, no
 * account/closed-date columns. References to the taxonomy are plain id columns; the foreign keys
 * (including the composite one tying the sub-category to the category) live in the migration.
 */
@Entity
@Table(name = "investment_products")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentProductJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "investment_category_id", nullable = false)
  private UUID investmentCategoryId;

  @Column(name = "investment_subcategory_id")
  private UUID investmentSubcategoryId;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Column(name = "additional_notes", length = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH)
  private String additionalNotes;

  // F026 (ADR 0023): generalized ticker/segment, optional for any product.
  @Column(length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String ticker;

  @Column(name = "segment_id")
  private UUID segmentId;

  public InvestmentProductJpaEntity(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes,
      String ticker,
      UUID segmentId) {
    this.id = id;
    this.investmentCategoryId = investmentCategoryId;
    this.investmentSubcategoryId = investmentSubcategoryId;
    this.name = name;
    this.additionalNotes = additionalNotes;
    this.ticker = ticker;
    this.segmentId = segmentId;
  }
}
