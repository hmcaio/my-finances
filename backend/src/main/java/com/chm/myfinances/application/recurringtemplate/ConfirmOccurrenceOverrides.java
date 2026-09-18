package com.chm.myfinances.application.recurringtemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional overrides accepted when confirming a {@code PendingRecurringOccurrence} (F007 spec, PRD
 * S5.7/S6.5): {@code amount}/{@code date}/{@code accountId} fall back to the occurrence's own
 * defaults (the resolved {@code RecurringTemplateVersion}'s amount, the occurrence's {@code
 * dueDate}, and the template's {@code accountId}) when {@code null}. {@code description}/{@code
 * additionalNotes} likewise default to the template's own description/{@code null}.
 *
 * <p>{@code paymentMethodId} is the one field with no template-level default to fall back to -
 * {@code RecurringTemplate} (F007 spec's field list) deliberately has no payment-method field, but
 * {@code Transaction.paymentMethodId} is mandatory (F004), so confirming always requires picking
 * one. This is a gap the F007 spec/plan don't resolve explicitly; see this class's use in {@code
 * RecurringTemplateService.confirmPending} and the feature's final report for the reasoning.
 */
public record ConfirmOccurrenceOverrides(
    BigDecimal amount,
    LocalDate date,
    UUID accountId,
    UUID paymentMethodId,
    String description,
    String additionalNotes) {}
