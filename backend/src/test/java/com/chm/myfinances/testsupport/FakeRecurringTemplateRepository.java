package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link RecurringTemplateRepository}, shared across application-service
 * tests (same spirit as {@link FakeBudgetRepository}).
 */
public final class FakeRecurringTemplateRepository implements RecurringTemplateRepository {

  private final Map<UUID, RecurringTemplate> store = new HashMap<>();

  @Override
  public RecurringTemplate save(RecurringTemplate template) {
    store.put(template.getId(), template);
    return template;
  }

  @Override
  public Optional<RecurringTemplate> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<RecurringTemplate> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public List<RecurringTemplate> findAllActive() {
    return store.values().stream().filter(RecurringTemplate::isActive).toList();
  }

  @Override
  public List<RecurringTemplate> findByAccountId(UUID accountId) {
    return store.values().stream().filter(t -> t.getAccountId().equals(accountId)).toList();
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return store.values().stream().anyMatch(t -> t.getCategoryId().equals(categoryId));
  }
}
