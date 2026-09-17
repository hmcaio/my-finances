package com.chm.myfinances.domain.recurringtemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link RecurringTemplate} (ADR 0004: domain/application logic sits behind
 * ports, isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/recurringtemplate}.
 *
 * <p>No pagination - like F006's {@code BudgetRepository}, the number of recurring templates a
 * single-user household maintains is inherently small (F007 plan.md's own reasoning for keeping
 * {@code GET /api/recurring-templates} a plain list).
 */
public interface RecurringTemplateRepository {

  RecurringTemplate save(RecurringTemplate template);

  Optional<RecurringTemplate> findById(UUID id);

  List<RecurringTemplate> findAll();

  /** Every {@code active} template - what the catch-up job iterates (F007 spec). */
  List<RecurringTemplate> findAllActive();

  /**
   * Every template pointed at {@code accountId} - backs F003's account-closed port implementation,
   * which deactivates each one (PRD S5.4).
   */
  List<RecurringTemplate> findByAccountId(UUID accountId);
}
