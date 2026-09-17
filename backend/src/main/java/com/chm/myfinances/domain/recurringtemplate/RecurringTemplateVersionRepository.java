package com.chm.myfinances.domain.recurringtemplate;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link RecurringTemplateVersion} (ADR 0004). Implemented by an adapter in
 * {@code infrastructure/persistence/recurringtemplate}.
 */
public interface RecurringTemplateVersionRepository {

  RecurringTemplateVersion save(RecurringTemplateVersion version);

  Optional<RecurringTemplateVersion> findById(UUID id);

  /**
   * Every version for one {@code templateId}, in no particular order - callers resolve the
   * effective one via {@link RecurringTemplateVersion#resolveEffective}.
   */
  List<RecurringTemplateVersion> findByTemplateId(UUID templateId);

  /**
   * The version for the exact {@code (templateId, effectiveFrom)} pair, if one already exists -
   * backs {@code RecurringTemplateService.setCap}'s same-month "replace, don't duplicate" rule
   * (F006 {@code BudgetService.setCap} precedent).
   */
  Optional<RecurringTemplateVersion> findByTemplateIdAndEffectiveFrom(
      UUID templateId, YearMonth effectiveFrom);
}
