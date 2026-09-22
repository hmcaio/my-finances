package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import java.util.List;
import java.util.UUID;

/**
 * In-memory test double for {@link RecurringTemplateRepository}, shared across application-service
 * tests (same spirit as {@link FakeBudgetRepository}).
 */
public final class FakeRecurringTemplateRepository extends InMemoryRepository<RecurringTemplate>
    implements RecurringTemplateRepository {

  public FakeRecurringTemplateRepository() {
    super(RecurringTemplate::getId);
  }

  @Override
  public List<RecurringTemplate> findAllActive() {
    return values().stream().filter(RecurringTemplate::isActive).toList();
  }

  @Override
  public List<RecurringTemplate> findByAccountId(UUID accountId) {
    return values().stream().filter(t -> t.getAccountId().equals(accountId)).toList();
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return values().stream().anyMatch(t -> t.getCategoryId().equals(categoryId));
  }
}
