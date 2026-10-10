package com.chm.myfinances.application.transaction;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.paymentmethod.PaymentMethodNotFoundException;
import com.chm.myfinances.application.vehicle.VehicleNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Transaction}: create/edit/delete/findById/findAll (F004 spec). New ids come
 * from the {@link IdGenerator} port (ADR 0005). {@code type} is always derived from the target
 * category's own (immutable, F002) type - never accepted as caller input - so it can never drift
 * from the category it's denormalized from.
 *
 * <p>F025 (ADR 0022) made every create/edit/delete overload {@code @Transactional}: each now
 * performs two writes (the business change, then the audit entry) that must commit or roll back
 * together, same multi-write reasoning as every other instrumented use case in this codebase. Every
 * overload carries its own annotation rather than just the "final" one each other delegates to via
 * self-invocation, which bypasses the Spring proxy (backend {@code CLAUDE.md}'s "Transactions"
 * section) - so whichever overload an external caller actually hits (the controller's 10-argument
 * {@code create}/{@code edit}, {@code RecurringTemplateService}'s 8-argument {@code create}) is
 * itself the one the proxy intercepts.
 *
 * <p>Coordinates across four other aggregates' repository ports (category, account, payment method,
 * vehicle) to validate foreign references exist and, for account, is open - this is ordinary
 * application-layer orchestration (ADR 0004), not a domain-layer dependency: {@code
 * domain/transaction} itself never imports {@code domain.account}/{@code domain.category} beyond
 * the shared {@code CategoryType} enum, and never imports {@code domain.vehicle} at all.
 *
 * <p>F024 (ADR 0021) adds the fuel invariant, enforced here (not in {@code Transaction} itself,
 * since it crosses into the {@code Category} aggregate) on every create/edit path: {@code
 * fuelDetails} is present if and only if the target category is the dedicated fuel category ({@link
 * FuelDetailsCategoryMismatchException}, 400 - a self-contained request-shape error, not a
 * state-dependent one); when present, {@code fuelDetails.vehicleId} must reference an existing
 * {@code Vehicle} (404). Applies uniformly to every {@code create}/{@code edit} overload, including
 * the one {@code RecurringTemplateService} uses to confirm a pending occurrence - which is why
 * {@code RecurringTemplateService.requireCategory} rejects the fuel category up front (409, issue
 * #92): a template has nowhere to carry a fill-up's per-occurrence details, so it could never
 * satisfy this invariant at confirm time.
 */
@Service
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final AccountRepository accountRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final VehicleRepository vehicleRepository;
  private final InvestmentHoldingRepository investmentHoldingRepository;
  private final IdGenerator idGenerator;
  private final AuditRecorder auditRecorder;

  public TransactionService(
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      AccountRepository accountRepository,
      PaymentMethodRepository paymentMethodRepository,
      VehicleRepository vehicleRepository,
      InvestmentHoldingRepository investmentHoldingRepository,
      IdGenerator idGenerator,
      AuditRecorder auditRecorder) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.accountRepository = accountRepository;
    this.paymentMethodRepository = paymentMethodRepository;
    this.vehicleRepository = vehicleRepository;
    this.investmentHoldingRepository = investmentHoldingRepository;
    this.idGenerator = idGenerator;
    this.auditRecorder = auditRecorder;
  }

  @Transactional
  public Transaction create(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes) {
    return create(
        date, amount, categoryId, accountId, paymentMethodId, null, description, additionalNotes);
  }

  /**
   * Same as {@link #create(LocalDate, BigDecimal, UUID, UUID, UUID, String, String)}, but for a
   * transaction originating from a confirmed F007 {@code PendingRecurringOccurrence} - {@code
   * recurringTemplateVersionId} links it back to the specific {@code RecurringTemplateVersion} that
   * generated it (F007 spec's confirm flow, PRD S5.7). {@code RecurringTemplateService} calls this
   * overload instead of duplicating category/account/payment-method validation. Never carries fuel
   * details.
   */
  @Transactional
  public Transaction create(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes) {
    return create(
        date,
        amount,
        categoryId,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes,
        null);
  }

  /**
   * Same as the 9-argument overload, but with no {@code investmentHoldingId} (F026). Most existing
   * callers never set one.
   */
  @Transactional
  public Transaction create(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails) {
    return create(
        date,
        amount,
        categoryId,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes,
        fuelDetails,
        null);
  }

  /**
   * Full create, accepting optional {@code fuelDetails} (F024) and/or {@code investmentHoldingId}
   * (F026) - the overload {@code TransactionController} calls. {@code requireValidFuelShape}/
   * {@code requireValidInvestmentHoldingShape} each enforce their own category-gated XOR invariant
   * independently - fuel and dividend are two separate categories, never both at once in practice,
   * but nothing here assumes that.
   */
  @Transactional
  public Transaction create(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails,
      UUID investmentHoldingId) {
    Category category = requireCategory(categoryId);
    Account account = requireOpenAccount(accountId);
    requirePaymentMethod(paymentMethodId);
    requireValidFuelShape(category, fuelDetails);
    requireValidInvestmentHoldingShape(category, investmentHoldingId);

    Transaction transaction =
        Transaction.create(
            idGenerator.newId(),
            date,
            amount,
            categoryId,
            category.getType(),
            account.getId(),
            paymentMethodId,
            recurringTemplateVersionId,
            description,
            additionalNotes,
            fuelDetails,
            investmentHoldingId);
    Transaction saved = transactionRepository.save(transaction);
    auditRecorder.recordCreate(
        AuditEntityType.TRANSACTION,
        saved.getId(),
        saved.getDescription(),
        saved.toAuditSnapshot());
    return saved;
  }

  public Transaction findById(UUID id) {
    return transactionRepository
        .findById(id)
        .orElseThrow(() -> new TransactionNotFoundException(id));
  }

  public Page<Transaction> findAll(TransactionFilter filter, Pageable pageable) {
    return transactionRepository.findAll(filter, pageable);
  }

  /**
   * Every fuel-purchase transaction for {@code vehicleId} within {@code from}/{@code to} (either
   * may be {@code null} - unbounded), ordered by date - backs the Fuel page's per-vehicle history
   * endpoint (F024 spec).
   */
  public List<Transaction> findFuelHistory(UUID vehicleId, LocalDate from, LocalDate to) {
    return transactionRepository.findByVehicleId(vehicleId, from, to);
  }

  @Transactional
  public Transaction edit(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes) {
    return edit(
        id,
        date,
        amount,
        categoryId,
        accountId,
        paymentMethodId,
        description,
        additionalNotes,
        null);
  }

  /**
   * Same as the 10-argument overload, but with no {@code investmentHoldingId} (F026) - clears any
   * previously recorded one.
   */
  @Transactional
  public Transaction edit(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails) {
    return edit(
        id,
        date,
        amount,
        categoryId,
        accountId,
        paymentMethodId,
        description,
        additionalNotes,
        fuelDetails,
        null);
  }

  /**
   * Full-replace edit including {@code fuelDetails} (F024) and/or {@code investmentHoldingId}
   * (F026) - the overload the controller calls.
   */
  @Transactional
  public Transaction edit(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes,
      FuelDetails fuelDetails,
      UUID investmentHoldingId) {
    Transaction transaction = findById(id);
    Map<String, Object> before = transaction.toAuditSnapshot();
    Category category = requireCategory(categoryId);
    Account account = requireOpenAccount(accountId);
    requirePaymentMethod(paymentMethodId);
    requireValidFuelShape(category, fuelDetails);
    requireValidInvestmentHoldingShape(category, investmentHoldingId);

    transaction.edit(
        date,
        amount,
        categoryId,
        category.getType(),
        account.getId(),
        paymentMethodId,
        description,
        additionalNotes,
        fuelDetails,
        investmentHoldingId);
    Transaction saved = transactionRepository.save(transaction);
    auditRecorder.recordUpdate(
        AuditEntityType.TRANSACTION,
        saved.getId(),
        saved.getDescription(),
        before,
        saved.toAuditSnapshot());
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    Transaction transaction = findById(id);
    transactionRepository.deleteById(id);
    auditRecorder.recordDelete(
        AuditEntityType.TRANSACTION,
        transaction.getId(),
        transaction.getDescription(),
        transaction.toAuditSnapshot());
  }

  private Category requireCategory(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new CategoryNotFoundException(categoryId));
  }

  private void requirePaymentMethod(UUID paymentMethodId) {
    if (!paymentMethodRepository.existsById(paymentMethodId)) {
      throw new PaymentMethodNotFoundException(paymentMethodId);
    }
  }

  /**
   * Resolves an account and rejects a closed one (F004 spec: "reject inserting a transaction
   * against a closed Account"). Applied on both create and edit - edit can move a transaction onto
   * a different, possibly-closed account just as easily as create can target one directly.
   */
  private Account requireOpenAccount(UUID accountId) {
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
    account.requireOpen();
    return account;
  }

  /**
   * The F024 fuel invariant (see class javadoc): {@code fuelDetails} present iff {@code category}
   * is the fuel category, and when present its {@code vehicleId} must resolve to an existing {@code
   * Vehicle}.
   */
  private void requireValidFuelShape(Category category, FuelDetails fuelDetails) {
    boolean hasFuelDetails = fuelDetails != null;
    if (category.isFuelCategory() != hasFuelDetails) {
      throw new FuelDetailsCategoryMismatchException();
    }
    if (hasFuelDetails && !vehicleRepository.existsById(fuelDetails.vehicleId())) {
      throw new VehicleNotFoundException(fuelDetails.vehicleId());
    }
  }

  /**
   * The F026 dividend invariant (see class javadoc): {@code investmentHoldingId} present iff {@code
   * category} is the dividend category, and when present it must resolve to an existing {@code
   * InvestmentHolding}.
   */
  private void requireValidInvestmentHoldingShape(Category category, UUID investmentHoldingId) {
    boolean hasInvestmentHoldingId = investmentHoldingId != null;
    if (category.isDividendCategory() != hasInvestmentHoldingId) {
      throw new InvestmentHoldingCategoryMismatchException();
    }
    if (hasInvestmentHoldingId && !investmentHoldingRepository.existsById(investmentHoldingId)) {
      throw new InvestmentHoldingNotFoundException(investmentHoldingId);
    }
  }
}
