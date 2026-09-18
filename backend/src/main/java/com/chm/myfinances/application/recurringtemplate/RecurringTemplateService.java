package com.chm.myfinances.application.recurringtemplate;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link RecurringTemplate}/{@link RecurringTemplateVersion}/{@link
 * PendingRecurringOccurrence} (F007 spec): create (with its first version), findById/findAll,
 * set-cap (new version, or same-month replace - same convention as F006's {@code
 * BudgetService.setCap}), stop/reactivate, the account-closed auto-deactivation path consumed by
 * {@code RealAccountClosedNotifier}, and the pending-occurrence list/confirm/dismiss flow. New ids
 * come from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Coordinates against F002's {@link CategoryRepository} and F003's {@link AccountRepository} to
 * validate a new template's targets exist and the account is open - ordinary application-layer
 * orchestration (ADR 0004), not a domain-layer dependency, same convention as {@code
 * TransactionService}/{@code TransferService}. Confirming a pending occurrence delegates to F004's
 * {@link TransactionService} (its {@code recurringTemplateVersionId}-aware overload) rather than
 * building a {@code Transaction} directly, so category/account/payment-method validation isn't
 * duplicated. {@code confirmPending}/{@code stop} are {@code @Transactional} since each performs
 * two writes (create-transaction + delete-pending, deactivate + bulk-delete-pending respectively)
 * that must commit or roll back together - this app has no other transaction-boundary handling, so
 * each such multi-write use case must opt in explicitly.
 */
@Service
public class RecurringTemplateService {

  private final RecurringTemplateRepository templateRepository;
  private final RecurringTemplateVersionRepository versionRepository;
  private final PendingRecurringOccurrenceRepository pendingRepository;
  private final CategoryRepository categoryRepository;
  private final AccountRepository accountRepository;
  private final TransactionService transactionService;
  private final RecurringOccurrenceCatchUpService catchUpService;
  private final IdGenerator idGenerator;

  public RecurringTemplateService(
      RecurringTemplateRepository templateRepository,
      RecurringTemplateVersionRepository versionRepository,
      PendingRecurringOccurrenceRepository pendingRepository,
      CategoryRepository categoryRepository,
      AccountRepository accountRepository,
      TransactionService transactionService,
      RecurringOccurrenceCatchUpService catchUpService,
      IdGenerator idGenerator) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.pendingRepository = pendingRepository;
    this.categoryRepository = categoryRepository;
    this.accountRepository = accountRepository;
    this.transactionService = transactionService;
    this.catchUpService = catchUpService;
    this.idGenerator = idGenerator;
  }

  /**
   * Creates a RecurringTemplate for {@code categoryId}/{@code accountId} plus its first {@link
   * RecurringTemplateVersion} (F007 spec). Rejects an unknown category/account (404) and a closed
   * account (409) - a template can't be created to post against an account that can't accept new
   * activity, same reasoning F004/F005 apply at transaction/transfer creation.
   */
  public RecurringTemplate create(
      UUID categoryId,
      UUID accountId,
      String description,
      BigDecimal amount,
      int dayOfMonth,
      YearMonth effectiveFrom) {
    requireCategory(categoryId);
    requireOpenAccount(accountId);

    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplate.create(idGenerator.newId(), categoryId, accountId, description));
    versionRepository.save(
        RecurringTemplateVersion.create(
            idGenerator.newId(), template.getId(), amount, dayOfMonth, effectiveFrom));
    return template;
  }

  public RecurringTemplate findById(UUID id) {
    return templateRepository
        .findById(id)
        .orElseThrow(() -> new RecurringTemplateNotFoundException(id));
  }

  public List<RecurringTemplate> findAll() {
    return templateRepository.findAll();
  }

  /**
   * Looks up a specific {@link RecurringTemplateVersion} by id - used by the web layer to render a
   * {@link PendingRecurringOccurrence}'s amount ({@code RecurringTemplateController}) without
   * exposing the repository port directly to that layer.
   */
  public RecurringTemplateVersion findVersionById(UUID versionId) {
    return versionRepository
        .findById(versionId)
        .orElseThrow(() -> new RecurringTemplateNotFoundException(versionId));
  }

  /**
   * Sets the amount/day-of-month effective from {@code effectiveFrom} (F007 spec's {@code PATCH
   * .../cap}): replaces the existing version for that exact month if one already exists, otherwise
   * creates a brand-new forward-only version - identical rule to F006's {@code
   * BudgetService.setCap}.
   *
   * <p>A same-month correction needs no further work: existing {@code PendingRecurringOccurrence}
   * rows already reference that version's id, and {@code RecurringTemplateController} resolves an
   * occurrence's displayed amount/day live via {@link #findVersionById}, so the mutated version's
   * new values show up automatically. A brand-new forward version is different - any occurrence
   * already generated for a cycle this new version now covers still points at the version that used
   * to be effective for that cycle, so it would keep showing the pre-edit amount/day until
   * confirmed. {@link #realignPendingOccurrences} fixes that by re-resolving, for every
   * still-pending occurrence of this template, which version {@link
   * RecurringTemplateVersion#resolveEffective} says should apply now and repointing it if that
   * changed - an occurrence whose cycle is still correctly covered by an older version (this edit's
   * {@code effectiveFrom} is later than that cycle) is left untouched, preserving what past months
   * showed (F007 spec).
   */
  @Transactional
  public RecurringTemplateVersion setCap(
      UUID templateId, BigDecimal amount, int dayOfMonth, YearMonth effectiveFrom) {
    RecurringTemplate template = findById(templateId);
    Optional<RecurringTemplateVersion> existing =
        versionRepository.findByTemplateIdAndEffectiveFrom(template.getId(), effectiveFrom);
    if (existing.isPresent()) {
      RecurringTemplateVersion version = existing.get();
      version.update(amount, dayOfMonth);
      return versionRepository.save(version);
    }
    RecurringTemplateVersion version =
        RecurringTemplateVersion.create(
            idGenerator.newId(), template.getId(), amount, dayOfMonth, effectiveFrom);
    RecurringTemplateVersion saved = versionRepository.save(version);
    realignPendingOccurrences(template.getId());
    return saved;
  }

  /**
   * Re-resolves which {@link RecurringTemplateVersion} each of {@code templateId}'s still-pending
   * occurrences should point to, repointing any whose correct version changed - see {@link
   * #setCap}.
   */
  private void realignPendingOccurrences(UUID templateId) {
    List<RecurringTemplateVersion> versions = versionRepository.findByTemplateId(templateId);
    for (PendingRecurringOccurrence occurrence : pendingRepository.findByTemplateId(templateId)) {
      RecurringTemplateVersion correctVersion =
          RecurringTemplateVersion.resolveEffective(
                  versions, YearMonth.from(occurrence.getDueDate()))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No RecurringTemplateVersion covers pending occurrence "
                              + occurrence.getId()));
      if (!correctVersion.getId().equals(occurrence.getTemplateVersionId())) {
        pendingRepository.save(
            PendingRecurringOccurrence.reconstitute(
                occurrence.getId(),
                occurrence.getTemplateId(),
                correctVersion.getId(),
                occurrence.getDueDate()));
      }
    }
  }

  /**
   * Stops a template (F007 spec's {@code POST .../stop}): deactivates it and deletes every
   * still-pending occurrence for it (F007 spec: "deleted once confirmed ... or the template is
   * deactivated"). Also the path {@link #deactivateForAccount} calls for F003's account-closed port
   * - the same operation regardless of trigger.
   */
  @Transactional
  public RecurringTemplate stop(UUID id) {
    RecurringTemplate template = findById(id);
    template.close();
    RecurringTemplate saved = templateRepository.save(template);
    pendingRepository.deleteByTemplateId(id);
    return saved;
  }

  /**
   * Reactivates a template (F007 spec's {@code POST .../reactivate}), resuming generation from now
   * rather than catching up on the entire stopped period (PRD S5.7).
   */
  public RecurringTemplate reactivate(UUID id) {
    RecurringTemplate template = findById(id);
    template.reactivate(YearMonth.now());
    return templateRepository.save(template);
  }

  /**
   * Deactivates every active template pointed at {@code accountId} - called by {@code
   * RealAccountClosedNotifier} when F003's {@code AccountClosedNotifier} port fires (PRD S5.4:
   * "Closing an account auto-deactivates ... any RecurringTemplate still pointing at it").
   */
  public void deactivateForAccount(UUID accountId) {
    for (RecurringTemplate template : templateRepository.findByAccountId(accountId)) {
      if (template.isActive()) {
        stop(template.getId());
      }
    }
  }

  /**
   * Every pending occurrence, dashboard-ready (F007 spec's {@code GET .../pending}) - triggers
   * catch-up generation first, so a request is never served stale (F007 spec: "triggers catch-up
   * generation first").
   */
  public List<PendingRecurringOccurrence> findAllPending() {
    catchUpService.runCatchUp();
    return pendingRepository.findAll();
  }

  /**
   * Confirms a pending occurrence into a real {@link Transaction} (F007 spec, PRD S5.7/S6.5), using
   * the occurrence's resolved version/date/template-account as defaults and applying any {@code
   * overrides} on top - the override never touches the template's own version history, only the
   * resulting transaction. Deletes the pending row once confirmed.
   */
  @Transactional
  public Transaction confirmPending(UUID pendingId, ConfirmOccurrenceOverrides overrides) {
    PendingRecurringOccurrence pending =
        pendingRepository
            .findById(pendingId)
            .orElseThrow(() -> new PendingRecurringOccurrenceNotFoundException(pendingId));
    RecurringTemplate template = findById(pending.getTemplateId());
    RecurringTemplateVersion version =
        versionRepository
            .findById(pending.getTemplateVersionId())
            .orElseThrow(
                () -> new RecurringTemplateNotFoundException(pending.getTemplateVersionId()));

    LocalDate date = overrides.date() != null ? overrides.date() : pending.getDueDate();
    BigDecimal amount = overrides.amount() != null ? overrides.amount() : version.getAmount();
    UUID accountId =
        overrides.accountId() != null ? overrides.accountId() : template.getAccountId();
    String description =
        overrides.description() != null ? overrides.description() : template.getDescription();

    Transaction transaction =
        transactionService.create(
            date,
            amount,
            template.getCategoryId(),
            accountId,
            overrides.paymentMethodId(),
            version.getId(),
            description,
            overrides.additionalNotes());

    pendingRepository.deleteById(pendingId);
    return transaction;
  }

  /**
   * Dismisses a pending occurrence without confirming it (F007 spec's {@code DELETE
   * .../pending/{id}}).
   */
  public void dismissPending(UUID pendingId) {
    if (pendingRepository.findById(pendingId).isEmpty()) {
      throw new PendingRecurringOccurrenceNotFoundException(pendingId);
    }
    pendingRepository.deleteById(pendingId);
  }

  private void requireCategory(UUID categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new CategoryNotFoundException(categoryId);
    }
  }

  private void requireOpenAccount(UUID accountId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (account.isClosed()) {
      throw new AccountClosedException(accountId);
    }
  }
}
