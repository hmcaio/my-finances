package com.chm.myfinances.infrastructure.web.recurringtemplate;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Request body for {@code POST /api/recurring-templates} (F007 spec): a target category/account
 * plus the template's first {@code RecurringTemplateVersion}. The category/account must resolve to
 * an existing category/an existing, open account - both checked at the application layer ({@code
 * RecurringTemplateService}), since neither can be expressed as a per-field bean validation
 * constraint.
 */
public record CreateRecurringTemplateRequest(
    @NotNull UUID categoryId,
    @NotNull UUID accountId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @NotNull @Positive BigDecimal amount,
    @Min(1) @Max(31) int dayOfMonth,
    @NotNull YearMonth effectiveFrom) {}
