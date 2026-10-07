package com.chm.myfinances.infrastructure.persistence.vehicle;

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

/** JPA mapping for the {@code vehicles} table (F024 spec). */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class VehicleJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  public VehicleJpaEntity(UUID id, String name) {
    this.id = id;
    this.name = name;
  }
}
