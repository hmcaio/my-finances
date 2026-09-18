package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link RecurringTemplateVersionRepository}, shared across
 * application-service tests (same spirit as {@link FakeBudgetVersionRepository}).
 */
public final class FakeRecurringTemplateVersionRepository
    implements RecurringTemplateVersionRepository {

  private final Map<UUID, RecurringTemplateVersion> store = new HashMap<>();

  @Override
  public RecurringTemplateVersion save(RecurringTemplateVersion version) {
    store.put(version.getId(), version);
    return version;
  }

  @Override
  public Optional<RecurringTemplateVersion> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<RecurringTemplateVersion> findByTemplateId(UUID templateId) {
    return store.values().stream().filter(v -> v.getTemplateId().equals(templateId)).toList();
  }

  @Override
  public Optional<RecurringTemplateVersion> findByTemplateIdAndEffectiveFrom(
      UUID templateId, YearMonth effectiveFrom) {
    return store.values().stream()
        .filter(v -> v.getTemplateId().equals(templateId))
        .filter(v -> v.getEffectiveFrom().equals(effectiveFrom))
        .findFirst();
  }
}
