package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

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
 * JPA mapping for the {@code recurring_templates} table (F007 spec). {@code categoryId}/{@code
 * accountId} are plain {@code UUID} columns, not JPA associations - same standalone-aggregate style
 * as {@code BudgetJpaEntity}/{@code TransactionJpaEntity}. {@code lastGeneratedFor} is stored as a
 * {@code date} (always the first day of the month, nullable until the first catch-up run) - the
 * domain's {@code YearMonth} is converted to/from that convention only at {@code
 * RecurringTemplateRepositoryAdapter} (ADR 0004).
 */
@Entity
@Table(name = "recurring_templates")
@Getter
@Setter
@NoArgsConstructor
public class RecurringTemplateJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "last_generated_for")
  private LocalDate lastGeneratedFor;

  public RecurringTemplateJpaEntity(
      UUID id,
      UUID categoryId,
      UUID accountId,
      String description,
      boolean active,
      LocalDate lastGeneratedFor) {
    this.id = id;
    this.categoryId = categoryId;
    this.accountId = accountId;
    this.description = description;
    this.active = active;
    this.lastGeneratedFor = lastGeneratedFor;
  }
}
