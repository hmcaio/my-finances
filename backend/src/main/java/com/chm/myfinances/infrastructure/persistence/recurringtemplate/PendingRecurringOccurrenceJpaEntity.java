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
 * JPA mapping for the {@code pending_recurring_occurrences} table (F007 spec) - a lightweight,
 * short-lived row deleted once its occurrence is confirmed or dismissed.
 */
@Entity
@Table(name = "pending_recurring_occurrences")
@Getter
@Setter
@NoArgsConstructor
public class PendingRecurringOccurrenceJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(name = "template_version_id", nullable = false)
  private UUID templateVersionId;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  public PendingRecurringOccurrenceJpaEntity(
      UUID id, UUID templateId, UUID templateVersionId, LocalDate dueDate) {
    this.id = id;
    this.templateId = templateId;
    this.templateVersionId = templateVersionId;
    this.dueDate = dueDate;
  }
}
