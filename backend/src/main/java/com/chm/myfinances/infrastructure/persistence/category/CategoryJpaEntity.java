package com.chm.myfinances.infrastructure.persistence.category;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.NameConstraints;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code categories} table (F002 spec). Maps directly to the domain's {@link
 * CategoryType} enum for the {@code type} column — a plain 2-value classification with no behavior
 * of its own, so a separate infrastructure-layer enum would just be duplicate mapping ceremony (ADR
 * 0004's framework-isolation intent targets domain *logic*, not simple value types).
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class CategoryJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = NameConstraints.MAX_NAME_LENGTH)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CategoryType type;

  public CategoryJpaEntity(UUID id, String name, CategoryType type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }
}
