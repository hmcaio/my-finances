package com.chm.myfinances.infrastructure.persistence.institution;

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
 * JPA mapping for the {@code institutions} table (F017 spec).
 *
 * <p>{@code builtIn} is read-only from the application's point of view: the only constructor always
 * writes {@code false}, there is no setter, and the column is {@code updatable = false}. The single
 * {@code true} row is inserted by {@code V12__institutions.sql}.
 */
@Entity
@Table(name = "institutions")
@Getter
@NoArgsConstructor
public class InstitutionJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Setter
  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  @Column(name = "built_in", nullable = false, updatable = false)
  private boolean builtIn;

  /** For a brand-new row; never built-in. */
  public InstitutionJpaEntity(UUID id, String name) {
    this.id = id;
    this.name = name;
    this.builtIn = false;
  }
}
