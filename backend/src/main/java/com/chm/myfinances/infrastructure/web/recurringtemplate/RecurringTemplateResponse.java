package com.chm.myfinances.infrastructure.web.recurringtemplate;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * API representation of a {@link RecurringTemplate} (F007 spec). {@code currentAmount}/{@code
 * currentDayOfMonth}/{@code currentEffectiveFrom} reflect whichever {@link
 * RecurringTemplateVersion} is effective as of the current real-world month ({@code
 * RecurringTemplateCurrentVersionQuery}, resolved by {@code RecurringTemplateController}) - all
 * three are {@code null} on the rare case where a template exists but no version is effective yet
 * (its only version's {@code effectiveFrom} is still in the future), same edge case F006's {@code
 * BudgetResponse} documents.
 */
public record RecurringTemplateResponse(
    UUID id,
    UUID categoryId,
    UUID accountId,
    String description,
    boolean active,
    BigDecimal currentAmount,
    Integer currentDayOfMonth,
    YearMonth currentEffectiveFrom) {

  public static RecurringTemplateResponse from(
      RecurringTemplate template, Optional<RecurringTemplateVersion> currentVersion) {
    return new RecurringTemplateResponse(
        template.getId(),
        template.getCategoryId(),
        template.getAccountId(),
        template.getDescription(),
        template.isActive(),
        currentVersion.map(RecurringTemplateVersion::getAmount).orElse(null),
        currentVersion.map(RecurringTemplateVersion::getDayOfMonth).orElse(null),
        currentVersion.map(RecurringTemplateVersion::getEffectiveFrom).orElse(null));
  }
}
