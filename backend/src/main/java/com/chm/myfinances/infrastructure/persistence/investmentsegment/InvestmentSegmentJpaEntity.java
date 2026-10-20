package com.chm.myfinances.infrastructure.persistence.investmentsegment;

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

/** JPA mapping for the {@code investment_segments} table (F026 spec). */
@Entity
@Table(name = "investment_segments")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentSegmentJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, length = TextFieldConstraints.MAX_NAME_LENGTH)
  private String name;

  public InvestmentSegmentJpaEntity(UUID id, String name) {
    this.id = id;
    this.name = name;
  }
}
