package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import com.chm.myfinances.domain.shared.NameConstraints;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA mapping for the {@code payment_methods} table (F002 spec). */
@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
public class PaymentMethodJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = NameConstraints.MAX_NAME_LENGTH)
  private String name;

  public PaymentMethodJpaEntity(UUID id, String name) {
    this.id = id;
    this.name = name;
  }
}
