package com.chm.myfinances.application.recurringtemplate;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves a {@code RecurringTemplate}'s currently-effective amount/day-of-month for display (F007
 * spec's {@code GET /api/recurring-templates} list) by loading every {@link
 * RecurringTemplateVersion} for a template and delegating to {@link
 * RecurringTemplateVersion#resolveEffective} - a computed-not-stored value, same "application-layer
 * query object" convention as F003's {@code AccountBalanceQuery} and F006's {@code BudgetCapQuery}.
 */
@Service
public class RecurringTemplateCurrentVersionQuery {

  private final RecurringTemplateVersionRepository versionRepository;

  public RecurringTemplateCurrentVersionQuery(
      RecurringTemplateVersionRepository versionRepository) {
    this.versionRepository = versionRepository;
  }

  public Optional<RecurringTemplateVersion> currentVersion(UUID templateId) {
    return RecurringTemplateVersion.resolveEffective(
        versionRepository.findByTemplateId(templateId), YearMonth.now());
  }
}
