package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link RecurringTemplateVersionRepository}, shared across
 * application-service tests (same spirit as {@link FakeBudgetVersionRepository}).
 */
public final class FakeRecurringTemplateVersionRepository
    extends InMemoryRepository<RecurringTemplateVersion>
    implements RecurringTemplateVersionRepository {

  public FakeRecurringTemplateVersionRepository() {
    super(RecurringTemplateVersion::getId);
  }

  @Override
  public List<RecurringTemplateVersion> findByTemplateId(UUID templateId) {
    return values().stream().filter(v -> v.getTemplateId().equals(templateId)).toList();
  }

  @Override
  public Optional<RecurringTemplateVersion> findByTemplateIdAndEffectiveFrom(
      UUID templateId, YearMonth effectiveFrom) {
    return values().stream()
        .filter(v -> v.getTemplateId().equals(templateId))
        .filter(v -> v.getEffectiveFrom().equals(effectiveFrom))
        .findFirst();
  }
}
