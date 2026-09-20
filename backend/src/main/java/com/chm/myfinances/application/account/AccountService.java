package com.chm.myfinances.application.account;

import com.chm.myfinances.application.institution.InstitutionNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountClosedNotifier;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Account}: create/edit/close/findById/findAll (F003 spec). New ids come from
 * the {@link IdGenerator} port (ADR 0005) - never generated ad hoc here or left to the database.
 *
 * <p>No delete use case - accounts are never hard-deleted (PRD S5.4/S8), only closed.
 *
 * <p>Create/edit reject a duplicate name (409, {@link AccountNameAlreadyExistsException}) - exact
 * match, case-sensitive, backed by {@code accounts.name UNIQUE} ({@code
 * V10__db_constraint_hardening.sql}), added in the post-F007 schema audit alongside the same guard
 * on F002's {@code Category}/{@code PaymentMethod}.
 */
@Service
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);

  private final AccountRepository accountRepository;
  private final InstitutionRepository institutionRepository;
  private final IdGenerator idGenerator;
  private final AccountClosedNotifier accountClosedNotifier;

  public AccountService(
      AccountRepository accountRepository,
      InstitutionRepository institutionRepository,
      IdGenerator idGenerator,
      AccountClosedNotifier accountClosedNotifier) {
    this.accountRepository = accountRepository;
    this.institutionRepository = institutionRepository;
    this.idGenerator = idGenerator;
    this.accountClosedNotifier = accountClosedNotifier;
  }

  public Account create(
      String name,
      UUID institutionId,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate) {
    if (accountRepository.existsByName(name)) {
      throw new AccountNameAlreadyExistsException(name);
    }
    requireInstitutionExists(institutionId);
    Account account =
        Account.create(
            idGenerator.newId(), name, institutionId, type, openingBalance, openingBalanceDate);
    return accountRepository.save(account);
  }

  public Account findById(UUID id) {
    return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
  }

  /**
   * Lists accounts. Closed accounts are excluded by default - they drop out of "create new" pickers
   * and the live balances widget, but stay browsable via {@link #findById(UUID)} (PRD S5.4).
   */
  public List<Account> findAll(boolean includeClosed) {
    List<Account> all = accountRepository.findAll();
    if (includeClosed) {
      return all;
    }
    return all.stream().filter(account -> !account.isClosed()).toList();
  }

  /** Edits name/institution only - type and opening balance/date are immutable (F003 spec). */
  public Account edit(UUID id, String name, UUID institutionId) {
    Account account = findById(id);
    if (accountRepository.existsByNameAndIdNot(name, id)) {
      throw new AccountNameAlreadyExistsException(name);
    }
    requireInstitutionExists(institutionId);
    account.edit(name, institutionId);
    return accountRepository.save(account);
  }

  /** The domain only holds the institution id; that it resolves is checked here (F017 spec). */
  private void requireInstitutionExists(UUID institutionId) {
    if (!institutionRepository.existsById(institutionId)) {
      throw new InstitutionNotFoundException(institutionId);
    }
  }

  /**
   * Closes an account and notifies {@link AccountClosedNotifier} so F007 can deactivate any
   * dependent {@code RecurringTemplate} without F003 depending on F007's package.
   *
   * <p>{@code @Transactional}: the account save commits immediately on its own (a single write is
   * self-transactional), but {@code accountClosedNotifier.accountClosed(...)} cascades into F007's
   * {@code RecurringTemplateService.deactivateForAccount} - a loop of {@code stop()} calls, each
   * already {@code @Transactional} itself. Without this annotation, a failure partway through that
   * cascade would leave the account committed closed while some dependent templates stay active,
   * and the caller would see a 500 implying the whole close failed when it actually partially
   * succeeded (retrying then only hits {@link AccountAlreadyClosedException}, never fixing the
   * templates). Wrapping this method makes the whole cascade commit or roll back together -
   * Spring's default {@code REQUIRED} propagation means {@code stop()}'s own {@code @Transactional}
   * just joins this one.
   */
  @Transactional
  public Account close(UUID id) {
    Account account = findById(id);
    if (account.isClosed()) {
      throw new AccountAlreadyClosedException(id);
    }
    account.close();
    Account saved = accountRepository.save(account);
    log.info("Account {} closed", saved.getId());
    accountClosedNotifier.accountClosed(saved.getId());
    return saved;
  }
}
