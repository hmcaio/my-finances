package com.chm.myfinances.application.investmentholding;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentproduct.InvestmentAccountRequiredException;
import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.investmentholding.HasHoldingHistoryChecker;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link InvestmentHolding} (F022 spec, ADR 0020): the many-to-many link between an
 * {@code InvestmentProduct} and the {@code INVESTMENT} account it's held in. New ids come from the
 * {@link IdGenerator} port (ADR 0005).
 *
 * <p>{@link #create} verifies, in order: the product exists (404); the account exists (404) and is
 * an open {@code INVESTMENT} account (409, {@link InvestmentAccountRequiredException}, reused from
 * F008); the pair isn't already a holding (409, {@link InvestmentHoldingAlreadyExistsException}).
 * Holdings are created explicitly - recording a trade for a pair with no holding is rejected, never
 * auto-created (F022 spec's "Decisions"). {@link #close} carries F009's old close guard (latest
 * snapshot must be {@code 0} or absent); {@link #delete} is only allowed at zero history, as
 * reported by the {@link HasHoldingHistoryChecker} port; otherwise the user closes the holding
 * instead.
 */
@Service
public class InvestmentHoldingService {

  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final HasHoldingHistoryChecker historyChecker;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final AuditRecorder auditRecorder;

  public InvestmentHoldingService(
      InvestmentHoldingRepository holdingRepository,
      InvestmentProductRepository productRepository,
      AccountRepository accountRepository,
      HasHoldingHistoryChecker historyChecker,
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      IdGenerator idGenerator,
      Clock clock,
      AuditRecorder auditRecorder) {
    this.holdingRepository = holdingRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.historyChecker = historyChecker;
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.auditRecorder = auditRecorder;
  }

  @Transactional
  public InvestmentHolding create(UUID productId, UUID accountId, String additionalNotes) {
    if (!productRepository.existsById(productId)) {
      throw new InvestmentProductNotFoundException(productId);
    }
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (account.getType() != AccountType.INVESTMENT || account.isClosed()) {
      throw new InvestmentAccountRequiredException(accountId);
    }
    if (holdingRepository.existsByProductIdAndAccountId(productId, accountId)) {
      throw new InvestmentHoldingAlreadyExistsException(productId, accountId);
    }
    InvestmentHolding saved =
        holdingRepository.save(
            InvestmentHolding.create(idGenerator.newId(), productId, accountId, additionalNotes));
    auditRecorder.recordCreate(
        AuditEntityType.INVESTMENT_HOLDING, saved.getId(), null, saved.toAuditSnapshot());
    return saved;
  }

  public InvestmentHolding findById(UUID id) {
    return holdingRepository
        .findById(id)
        .orElseThrow(() -> new InvestmentHoldingNotFoundException(id));
  }

  /** Every holding of a product, across every account it's held in. 404 for an unknown product. */
  public List<InvestmentHolding> findByProduct(UUID productId) {
    if (!productRepository.existsById(productId)) {
      throw new InvestmentProductNotFoundException(productId);
    }
    return holdingRepository.findByProductId(productId);
  }

  /** Every holding in an account, across every product held there. 404 for an unknown account. */
  public List<InvestmentHolding> findByAccount(UUID accountId) {
    if (!accountRepository.existsById(accountId)) {
      throw new AccountNotFoundException(accountId);
    }
    return holdingRepository.findByAccountId(accountId);
  }

  @Transactional
  public InvestmentHolding editNotes(UUID id, String additionalNotes) {
    InvestmentHolding holding = findById(id);
    Map<String, Object> before = holding.toAuditSnapshot();
    holding.editNotes(additionalNotes);
    InvestmentHolding saved = holdingRepository.save(holding);
    auditRecorder.recordUpdate(
        AuditEntityType.INVESTMENT_HOLDING, saved.getId(), null, before, saved.toAuditSnapshot());
    return saved;
  }

  /**
   * Closes a holding. One write, so no {@code @Transactional}. A holding can only be closed while
   * its latest snapshot is {@code 0} or absent (F009's rule, moved here by F022), so a closed
   * holding never keeps counting a stale value.
   */
  @Transactional
  public InvestmentHolding close(UUID id) {
    InvestmentHolding holding = findById(id);
    Map<String, Object> before = holding.toAuditSnapshot();
    if (holding.isClosed()) {
      throw new InvestmentHoldingAlreadyClosedException(id);
    }
    if (latestSnapshotQuery
        .latestOf(id)
        .filter(snapshot -> snapshot.getBalance().signum() != 0)
        .isPresent()) {
      throw new InvestmentHoldingNotEmptyException(id);
    }
    holding.close(LocalDate.now(clock));
    InvestmentHolding saved = holdingRepository.save(holding);
    auditRecorder.recordAction(
        AuditEntityType.INVESTMENT_HOLDING,
        saved.getId(),
        null,
        AuditAction.CLOSE,
        before,
        saved.toAuditSnapshot(),
        AuditOrigin.USER);
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    InvestmentHolding holding = findById(id);
    if (historyChecker.hasHistory(id)) {
      throw new InvestmentHoldingHasHistoryException(id);
    }
    holdingRepository.deleteById(id);
    auditRecorder.recordDelete(
        AuditEntityType.INVESTMENT_HOLDING, holding.getId(), null, holding.toAuditSnapshot());
  }

  /** Whether the holding has history - drives the API response's {@code hasHistory} flag. */
  public boolean hasHistory(UUID id) {
    findById(id);
    return historyChecker.hasHistory(id);
  }
}
