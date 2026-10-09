package com.chm.myfinances.application.recurringtemplate;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * duplicated. {@code create}/{@code confirmPending}/{@code stop}/{@code setCap} are all
 * {@code @Transactional} since each performs multiple writes (template + first version,
 * create-transaction + delete-pending, deactivate + bulk-delete-pending, new-version +
 * pending-occurrence realignment respectively) that must commit or roll back together - this app
 * has no other transaction-boundary handling, so each such multi-write use case must opt in
 * explicitly.
 */
@Service
public class RecurringTemplateService {

  private static final Logger log = LoggerFactory.getLogger(RecurringTemplateService.class);

  private final RecurringTemplateRepository templateRepository;
  private final RecurringTemplateVersionRepository versionRepository;
  private final PendingRecurringOccurrenceRepository pendingRepository;
  private final CategoryRepository categoryRepository;
  private final AccountRepository accountRepository;
  private final TransactionService transactionService;
  private final RecurringOccurrenceCatchUpService catchUpService;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final AuditRecorder auditRecorder;

  public RecurringTemplateService(
      RecurringTemplateRepository templateRepository,
      RecurringTemplateVersionRepository versionRepository,
      PendingRecurringOccurrenceRepository pendingRepository,
      CategoryRepository categoryRepository,
      AccountRepository accountRepository,
      TransactionService transactionService,
      RecurringOccurrenceCatchUpService catchUpService,
      IdGenerator idGenerator,
      Clock clock,
      AuditRecorder auditRecorder) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.pendingRepository = pendingRepository;
    this.categoryRepository = categoryRepository;
    this.accountRepository = accountRepository;
    this.transactionService = transactionService;
    this.catchUpService = catchUpService;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.auditRecorder = auditRecorder;
  }

  /**
   * Creates a RecurringTemplate for {@code categoryId}/{@code accountId} plus its first {@link
   * RecurringTemplateVersion} (F007 spec). Rejects an unknown category/account (404) and a closed
   * account (409) - a template can't be created to post against an account that can't accept new
   * activity, same reasoning F004/F005 apply at transaction/transfer creation. Also rejects an
   * {@code INVESTMENT} account and the fuel category (409, F024): both have no templatable "amount
   * only" shape - an investment trade needs a product via a transfer, a fuel purchase needs its
   * per-fill details (vehicle, liters, price, odometer) that a template has nowhere to carry - so
   * confirming a pending occurrence against either would otherwise fail later, confusingly, instead
   * of here where the mistake was actually made.
   *
   * <p>{@code @Transactional} since it's two writes (template + its first version) that must commit
   * or roll back together - a failure between them would otherwise leave a template with zero
   * versions, same multi-write reasoning as {@code confirmPending}/{@code stop}/{@code setCap}.
   */
  @Transactional
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
      RecurringTemplateVersion replaced = versionRepository.save(version);
      log.info(
          "Recurring template {}: version effective {} replaced", template.getId(), effectiveFrom);
      return replaced;
    }
    RecurringTemplateVersion version =
        RecurringTemplateVersion.create(
            idGenerator.newId(), template.getId(), amount, dayOfMonth, effectiveFrom);
    RecurringTemplateVersion saved = versionRepository.save(version);
    realignPendingOccurrences(template.getId());
    log.info("Recurring template {}: new version effective {}", template.getId(), effectiveFrom);
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
    return stop(id, AuditOrigin.USER);
  }

  /**
   * Same as {@link #stop(UUID)}, but lets a cascade (F025, ADR 0022: closing an account
   * deactivating its templates) record its entry as {@link AuditOrigin#SYSTEM} instead of {@link
   * AuditOrigin#USER}. {@code @Transactional} on both overloads since whichever one an external
   * caller actually hits is the one the Spring proxy intercepts (backend {@code CLAUDE.md}'s
   * "Transactions" section) - the public, no-origin {@link #stop(UUID)} delegates to this one via
   * self-invocation, which bypasses the proxy.
   */
  @Transactional
  public RecurringTemplate stop(UUID id, AuditOrigin origin) {
    RecurringTemplate template = findById(id);
    Map<String, Object> before = template.toAuditSnapshot();
    template.close();
    RecurringTemplate saved = templateRepository.save(template);
    pendingRepository.deleteByTemplateId(id);
    auditRecorder.recordAction(
        AuditEntityType.RECURRING_TEMPLATE,
        saved.getId(),
        saved.getDescription(),
        AuditAction.CLOSE,
        before,
        saved.toAuditSnapshot(),
        origin);
    return saved;
  }

  /**
   * Reactivates a template (F007 spec's {@code POST .../reactivate}), resuming generation from now
   * rather than catching up on the entire stopped period (PRD S5.7).
   */
  public RecurringTemplate reactivate(UUID id) {
    RecurringTemplate template = findById(id);
    template.reactivate(YearMonth.now(clock));
    return templateRepository.save(template);
  }

  /**
   * Deactivates every active template pointed at {@code accountId} - called by {@code
   * RealAccountClosedNotifier} when F003's {@code AccountClosedNotifier} port fires (PRD S5.4:
   * "Closing an account auto-deactivates ... any RecurringTemplate still pointing at it").
   *
   * <p>{@code @Transactional} defensively: in normal use this joins the caller's own transaction
   * (F003's {@code AccountService.close()} is itself {@code @Transactional}, so the whole cascade
   * already commits or rolls back together), but annotating it here too means a partial failure
   * across this loop of independently-{@code @Transactional} {@link #stop} calls is never left
   * half-done even if this method is ever called from a context that isn't already transactional.
   */
  @Transactional
  public void deactivateForAccount(UUID accountId) {
    int deactivated = 0;
    for (RecurringTemplate template : templateRepository.findByAccountId(accountId)) {
      if (template.isActive()) {
        stop(template.getId(), AuditOrigin.SYSTEM);
        deactivated++;
      }
    }
    if (deactivated > 0) {
      log.info("Deactivated {} template(s) for closed account {}", deactivated, accountId);
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
    log.info("Pending occurrence {} confirmed as transaction {}", pendingId, transaction.getId());
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
    log.info("Pending occurrence {} dismissed", pendingId);
  }

  private void requireCategory(UUID categoryId) {
    Category category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    if (category.isFuelCategory()) {
      throw new FuelCategoryNotAllowedException(categoryId);
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
    if (account.getType() == AccountType.INVESTMENT) {
      throw new AccountTypeNotAllowedException(accountId);
    }
  }
}
