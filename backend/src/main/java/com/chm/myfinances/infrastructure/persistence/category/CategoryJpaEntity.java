package com.chm.myfinances.infrastructure.persistence.category;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
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
 *
 * <p>{@code builtIn} is read-only from the application's point of view: the only constructor always
 * writes {@code false}, there is no setter, and the column is {@code updatable = false}. The {@code
 * true} rows are set by {@code V14__builtin_categories.sql}. Same shape for {@code fuelCategory}
 * (F024, ADR 0021), set by {@code V18}.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor
public class CategoryJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Setter
  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CategoryType type;

  @Column(name = "built_in", nullable = false, updatable = false)
  private boolean builtIn;

  @Column(name = "fuel_category", nullable = false, updatable = false)
  private boolean fuelCategory;

  /** For a brand-new row; never built-in, never the fuel category. */
  public CategoryJpaEntity(UUID id, String name, CategoryType type) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.builtIn = false;
    this.fuelCategory = false;
  }
}
