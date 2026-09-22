package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * In-memory test double for {@link PendingRecurringOccurrenceRepository}, shared across
 * application-service tests (same spirit as {@link FakeBudgetRepository}).
 */
public final class FakePendingRecurringOccurrenceRepository
    extends InMemoryRepository<PendingRecurringOccurrence>
    implements PendingRecurringOccurrenceRepository {

  public FakePendingRecurringOccurrenceRepository() {
    super(PendingRecurringOccurrence::getId);
  }

  @Override
  public void deleteByTemplateId(UUID templateId) {
    values().removeIf(o -> o.getTemplateId().equals(templateId));
  }

  @Override
  public boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate) {
    return values().stream()
        .anyMatch(o -> o.getTemplateId().equals(templateId) && o.getDueDate().equals(dueDate));
  }

  @Override
  public boolean insertIfAbsent(PendingRecurringOccurrence occurrence) {
    if (existsByTemplateIdAndDueDate(occurrence.getTemplateId(), occurrence.getDueDate())) {
      return false;
    }
    save(occurrence);
    return true;
  }

  @Override
  public List<PendingRecurringOccurrence> findByTemplateId(UUID templateId) {
    return values().stream().filter(o -> o.getTemplateId().equals(templateId)).toList();
  }
}
