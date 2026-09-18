package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code recurring_template_versions} table (F007 spec). {@code effectiveFrom}
 * is stored as a {@code date} - always the first day of the month - the domain's {@code YearMonth}
 * is converted to/from that convention only at {@code RecurringTemplateVersionRepositoryAdapter},
 * same convention as F006's {@code BudgetVersionJpaEntity}.
 */
@Entity
@Table(name = "recurring_template_versions")
@Getter
@Setter
@NoArgsConstructor
public class RecurringTemplateVersionJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "day_of_month", nullable = false)
  private int dayOfMonth;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  public RecurringTemplateVersionJpaEntity(
      UUID id, UUID templateId, BigDecimal amount, int dayOfMonth, LocalDate effectiveFrom) {
    this.id = id;
    this.templateId = templateId;
    this.amount = amount;
    this.dayOfMonth = dayOfMonth;
    this.effectiveFrom = effectiveFrom;
  }
}
