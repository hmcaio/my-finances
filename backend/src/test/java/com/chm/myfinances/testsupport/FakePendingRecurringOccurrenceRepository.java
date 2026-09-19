package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link PendingRecurringOccurrenceRepository}, shared across
 * application-service tests (same spirit as {@link FakeBudgetRepository}).
 */
public final class FakePendingRecurringOccurrenceRepository
    implements PendingRecurringOccurrenceRepository {

  private final Map<UUID, PendingRecurringOccurrence> store = new HashMap<>();

  @Override
  public PendingRecurringOccurrence save(PendingRecurringOccurrence occurrence) {
    store.put(occurrence.getId(), occurrence);
    return occurrence;
  }

  @Override
  public Optional<PendingRecurringOccurrence> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<PendingRecurringOccurrence> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public void deleteByTemplateId(UUID templateId) {
    store.values().removeIf(o -> o.getTemplateId().equals(templateId));
  }

  @Override
  public boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate) {
    return store.values().stream()
        .anyMatch(o -> o.getTemplateId().equals(templateId) && o.getDueDate().equals(dueDate));
  }

  @Override
  public boolean insertIfAbsent(PendingRecurringOccurrence occurrence) {
    if (existsByTemplateIdAndDueDate(occurrence.getTemplateId(), occurrence.getDueDate())) {
      return false;
    }
    store.put(occurrence.getId(), occurrence);
    return true;
  }

  @Override
  public List<PendingRecurringOccurrence> findByTemplateId(UUID templateId) {
    return store.values().stream().filter(o -> o.getTemplateId().equals(templateId)).toList();
  }
}
