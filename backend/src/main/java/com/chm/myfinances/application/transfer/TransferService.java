package com.chm.myfinances.application.transfer;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingClosedException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Transfer}: create/edit/delete/findById/findAll (F005 spec). New ids come
 * from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Coordinates against F003's {@link AccountRepository} to validate both accounts exist and are
 * open, and that they're not the same account - ordinary application-layer orchestration (ADR
 * 0004), not a domain-layer dependency: {@code domain/transfer} itself never imports {@code
 * domain.account}.
 *
 * <p>F027 (ADR 0024, superseding F009/F022's single-product shape) makes a transfer able to carry a
 * {@link TradeConfirmation}: one shared cash account, one shared {@code INVESTMENT} account, and
 * one-or-more lines. {@link #createTradeConfirmation}/{@link #editTradeConfirmation} validate, per
 * line, that {@code (productId, investmentAccountId)} resolves to an existing open {@code
 * InvestmentHolding} (404/409, generalizing F009/F022's single-product check), then write the
 * {@link Transfer} (amount/direction derived from {@link TradeConfirmation#netCost}) and, per line,
 * replace a same-day snapshot ({@code resultingBalance}) and/or close the holding ({@code
 * closeHolding}) - all inside one {@code @Transactional} use case (backend CLAUDE.md,
 * "Transactions"). {@link #requireExactlyOneRequestShape} is the create/edit mutual-exclusivity
 * rule between the plain {@code {fromAccountId, toAccountId, amount}} shape and the confirmation
 * shape (spec's Decisions) - called by {@code TransferController} before it decides which create/
 * edit overload to invoke, so each stays its own clean, directly {@code @Transactional} entry point
 * (no self-invocation, which would silently skip the Spring proxy and the transaction boundary it
 * provides).
 */
@Service
public class TransferService {

  private static final Logger log = LoggerFactory.getLogger(TransferService.class);

  private final TransferRepository transferRepository;
  private final AccountRepository accountRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentSnapshotService snapshotService;
  private final InvestmentHoldingService holdingService;
  private final IdGenerator idGenerator;
  private final AuditRecorder auditRecorder;

  public TransferService(
      TransferRepository transferRepository,
      AccountRepository accountRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentSnapshotService snapshotService,
      InvestmentHoldingService holdingService,
      IdGenerator idGenerator,
      AuditRecorder auditRecorder) {
    this.transferRepository = transferRepository;
    this.accountRepository = accountRepository;
    this.holdingRepository = holdingRepository;
    this.snapshotService = snapshotService;
    this.holdingService = holdingService;
    this.idGenerator = idGenerator;
    this.auditRecorder = auditRecorder;
  }

  /** Creates a plain (non-investment) transfer. */
  @Transactional
  public Transfer create(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    requireDifferentAccounts(fromAccountId, toAccountId);
    Account fromAccount = requireOpenAccount(fromAccountId);
    Account toAccount = requireOpenAccount(toAccountId);
    Transfer transfer =
        Transfer.create(
            idGenerator.newId(),
            date,
            fromAccount.getId(),
            toAccount.getId(),
            amount,
            description,
            additionalNotes);
    Transfer saved = transferRepository.save(transfer);
    auditRecorder.recordCreate(
        AuditEntityType.TRANSFER, saved.getId(), saved.getDescription(), saved.toAuditSnapshot());
    return saved;
  }

  /**
   * Creates a Transfer carrying a {@link TradeConfirmation} (F027): validates the cash/investment
   * accounts and every line's holding, writes the transfer with its backend-derived amount/
   * direction, then applies each line's {@code resultingBalance}/{@code closeHolding} side effects.
   * Two or more writes whenever any line effect applies, so {@code @Transactional}.
   */
  @Transactional
  public Transfer createTradeConfirmation(
      LocalDate date,
      UUID cashAccountId,
      UUID investmentAccountId,
      String description,
      String additionalNotes,
      BigDecimal taxes,
      List<TradeConfirmationLine> lines) {
    requireDifferentAccounts(cashAccountId, investmentAccountId);
    Account cashAccount = requireOpenAccount(cashAccountId);
    Account investmentAccount = requireOpenAccount(investmentAccountId);
    requireInvestmentShape(cashAccount, investmentAccount);
    TradeConfirmation confirmation = toConfirmation(lines);
    Map<UUID, InvestmentHolding> holdings = requireValidHoldings(confirmation, investmentAccount);

    Transfer transfer =
        createTransfer(
            date,
            cashAccount.getId(),
            investmentAccount.getId(),
            description,
            additionalNotes,
            taxes,
            confirmation);
    Transfer saved = transferRepository.save(transfer);
    auditRecorder.recordCreate(
        AuditEntityType.TRANSFER, saved.getId(), saved.getDescription(), saved.toAuditSnapshot());
    applyLineEffects(confirmation, holdings, date);
    log.info("Recorded trade confirmation as transfer {}", saved.getId());
    return saved;
  }

  private Transfer createTransfer(
      LocalDate date,
      UUID cashAccountId,
      UUID investmentAccountId,
      String description,
      String additionalNotes,
      BigDecimal taxes,
      TradeConfirmation confirmation) {
    try {
      return Transfer.createTradeConfirmation(
          idGenerator.newId(),
          date,
          cashAccountId,
          investmentAccountId,
          description,
          additionalNotes,
          taxes,
          confirmation);
    } catch (IllegalArgumentException e) {
      throw new InvalidTradeConfirmationException(e.getMessage());
    }
  }

  private TradeConfirmation toConfirmation(List<TradeConfirmationLine> lines) {
    try {
      return TradeConfirmation.of(lines);
    } catch (IllegalArgumentException e) {
      throw new InvalidTradeConfirmationException(e.getMessage());
    }
  }

  public Transfer findById(UUID id) {
    return transferRepository.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
  }

  public Page<Transfer> findAll(TransferFilter filter, Pageable pageable) {
    return transferRepository.findAll(filter, pageable);
  }

  /** Edits a plain (non-investment) transfer, clearing any trade confirmation it had. */
  @Transactional
  public Transfer edit(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    Transfer transfer = findById(id);
    Map<String, Object> before = transfer.toAuditSnapshot();
    requireDifferentAccounts(fromAccountId, toAccountId);
    Account fromAccount = requireOpenAccount(fromAccountId);
    Account toAccount = requireOpenAccount(toAccountId);
    transfer.edit(
        date, fromAccount.getId(), toAccount.getId(), amount, description, additionalNotes);
    Transfer saved = transferRepository.save(transfer);
    auditRecorder.recordUpdate(
        AuditEntityType.TRANSFER,
        saved.getId(),
        saved.getDescription(),
        before,
        saved.toAuditSnapshot());
    return saved;
  }

  /**
   * Full-replace edit of a Transfer's {@link TradeConfirmation} (F027): same validation and
   * per-line side effects as {@link #createTradeConfirmation}, recomputing amount/direction.
   */
  @Transactional
  public Transfer editTradeConfirmation(
      UUID id,
      LocalDate date,
      UUID cashAccountId,
      UUID investmentAccountId,
      String description,
      String additionalNotes,
      BigDecimal taxes,
      List<TradeConfirmationLine> lines) {
    Transfer transfer = findById(id);
    Map<String, Object> before = transfer.toAuditSnapshot();
    requireDifferentAccounts(cashAccountId, investmentAccountId);
    Account cashAccount = requireOpenAccount(cashAccountId);
    Account investmentAccount = requireOpenAccount(investmentAccountId);
    requireInvestmentShape(cashAccount, investmentAccount);
    TradeConfirmation confirmation = toConfirmation(lines);
    Map<UUID, InvestmentHolding> holdings = requireValidHoldings(confirmation, investmentAccount);

    try {
      transfer.editTradeConfirmation(
          date,
          cashAccount.getId(),
          investmentAccount.getId(),
          description,
          additionalNotes,
          taxes,
          confirmation);
    } catch (IllegalArgumentException e) {
      throw new InvalidTradeConfirmationException(e.getMessage());
    }
    Transfer saved = transferRepository.save(transfer);
    auditRecorder.recordUpdate(
        AuditEntityType.TRANSFER,
        saved.getId(),
        saved.getDescription(),
        before,
        saved.toAuditSnapshot());
    applyLineEffects(confirmation, holdings, date);
    log.info("Edited trade confirmation transfer {}", saved.getId());
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    Transfer transfer = findById(id);
    transferRepository.deleteById(id);
    auditRecorder.recordDelete(
        AuditEntityType.TRANSFER,
        transfer.getId(),
        transfer.getDescription(),
        transfer.toAuditSnapshot());
  }

  /**
   * The F027 create/edit mutual-exclusivity rule (ADR 0024, spec's Decisions): exactly one of
   * {@code {fromAccountId, toAccountId, amount}} (all required together) or {@code {cashAccountId,
   * investmentAccountId, lines}} (all required together) must be given. A static, state-independent
   * check - 400 either way, never a 409, since it's wrong regardless of what's persisted.
   */
  public static void requireExactlyOneRequestShape(
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      UUID cashAccountId,
      UUID investmentAccountId,
      List<TradeConfirmationLine> lines) {
    boolean anyPlain = fromAccountId != null || toAccountId != null || amount != null;
    boolean anyConfirmation = cashAccountId != null || investmentAccountId != null || lines != null;
    if (anyPlain == anyConfirmation) {
      throw new InvalidTradeConfirmationException(
          "exactly one of {fromAccountId, toAccountId, amount} or {cashAccountId,"
              + " investmentAccountId, tradeConfirmation} must be given");
    }
    if (anyPlain && (fromAccountId == null || toAccountId == null || amount == null)) {
      throw new InvalidTradeConfirmationException(
          "fromAccountId, toAccountId and amount must be given together");
    }
    if (anyConfirmation
        && (cashAccountId == null
            || investmentAccountId == null
            || lines == null
            || lines.isEmpty())) {
      throw new InvalidTradeConfirmationException(
          "cashAccountId, investmentAccountId and tradeConfirmation must be given together");
    }
  }

  /**
   * Rejects a transfer to/from the same account (F005 spec's invariant) before either account is
   * even looked up - checked at the application layer, mapped to a proper 400 here, with {@link
   * Transfer}'s own constructor/{@code edit} enforcing the same rule again at the domain level as
   * defense in depth.
   */
  private void requireDifferentAccounts(UUID fromAccountId, UUID toAccountId) {
    if (fromAccountId.equals(toAccountId)) {
      throw new SameAccountTransferException(fromAccountId);
    }
  }

  /**
   * Resolves an account and rejects a closed one (F005 spec: "Both accounts must be open ... at
   * creation time"). Applied on both create and edit - edit can move a transfer onto a different,
   * possibly-closed account just as easily as create can target one directly (same convention as
   * F004's {@code TransactionService.requireOpenAccount}).
   */
  private Account requireOpenAccount(UUID accountId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (account.isClosed()) {
      throw new AccountClosedException(accountId);
    }
    return account;
  }

  /**
   * A confirmation's two accounts (F027 spec): the investment-side account must actually be {@code
   * INVESTMENT}, and the cash-side account must not be (a confirmation spanning two investment
   * accounts isn't modeled, same as F009's original rule).
   */
  private void requireInvestmentShape(Account cashAccount, Account investmentAccount) {
    if (cashAccount.getType() == AccountType.INVESTMENT) {
      throw new InvestmentTransferInvalidException(
          "a transfer between two investment accounts is not supported");
    }
    if (investmentAccount.getType() != AccountType.INVESTMENT) {
      throw new InvestmentTransferInvalidException(
          "investmentAccountId must be an INVESTMENT account");
    }
  }

  /**
   * Per-line holding validation (F027 spec, generalizing F009/F022's single-product check): every
   * distinct product across the confirmation's lines must resolve to an existing, open {@code
   * InvestmentHolding} at {@code investmentAccount} (404 if no such holding, 409 if closed).
   * Returns the resolved holdings by product id so {@link #applyLineEffects} doesn't look them up
   * again.
   */
  private Map<UUID, InvestmentHolding> requireValidHoldings(
      TradeConfirmation confirmation, Account investmentAccount) {
    Map<UUID, InvestmentHolding> holdings = new LinkedHashMap<>();
    for (TradeConfirmationLine line : confirmation.getLines()) {
      holdings.computeIfAbsent(
          line.productId(), productId -> resolveOpenHolding(productId, investmentAccount));
    }
    return holdings;
  }

  private InvestmentHolding resolveOpenHolding(UUID productId, Account investmentAccount) {
    InvestmentHolding holding =
        holdingRepository
            .findByProductIdAndAccountId(productId, investmentAccount.getId())
            .orElseThrow(
                () -> new InvestmentHoldingNotFoundException(productId, investmentAccount.getId()));
    if (holding.isClosed()) {
      throw new InvestmentHoldingClosedException(holding.getId());
    }
    return holding;
  }

  /**
   * Per-line side effects (F027 spec): every line with a {@code resultingBalance} replaces that
   * holding's same-day snapshot first (so a same-line {@code closeHolding} sees it), then every
   * line with {@code closeHolding = true} closes its holding - same ordering guarantees the close
   * guard (latest snapshot zero/absent) sees the just-written snapshot.
   */
  private void applyLineEffects(
      TradeConfirmation confirmation, Map<UUID, InvestmentHolding> holdings, LocalDate date) {
    for (TradeConfirmationLine line : confirmation.getLines()) {
      if (line.resultingBalance() != null) {
        InvestmentHolding holding = holdings.get(line.productId());
        snapshotService.record(holding.getId(), date, line.resultingBalance());
      }
    }
    for (TradeConfirmationLine line : confirmation.getLines()) {
      if (line.closeHolding()) {
        InvestmentHolding holding = holdings.get(line.productId());
        holdingService.close(holding.getId());
      }
    }
  }
}
