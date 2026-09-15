package com.chm.myfinances.application.account;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountClosedNotifier;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Account}: create/edit/close/findById/findAll (F003 spec). New ids come from
 * the {@link IdGenerator} port (ADR 0005) - never generated ad hoc here or left to the database.
 *
 * <p>No delete use case - accounts are never hard-deleted (PRD S5.4/S8), only closed.
 */
@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final IdGenerator idGenerator;
  private final AccountClosedNotifier accountClosedNotifier;

  public AccountService(
      AccountRepository accountRepository,
      IdGenerator idGenerator,
      AccountClosedNotifier accountClosedNotifier) {
    this.accountRepository = accountRepository;
    this.idGenerator = idGenerator;
    this.accountClosedNotifier = accountClosedNotifier;
  }

  public Account create(
      String name,
      String institution,
      AccountType type,
      BigDecimal openingBalance,
      LocalDate openingBalanceDate) {
    Account account =
        Account.create(
            idGenerator.newId(), name, institution, type, openingBalance, openingBalanceDate);
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
  public Account edit(UUID id, String name, String institution) {
    Account account = findById(id);
    account.edit(name, institution);
    return accountRepository.save(account);
  }

  /**
   * Closes an account and notifies {@link AccountClosedNotifier} so F007 can deactivate any
   * dependent {@code RecurringTemplate} without F003 depending on F007's package.
   */
  public Account close(UUID id) {
    Account account = findById(id);
    if (account.isClosed()) {
      throw new AccountAlreadyClosedException(id);
    }
    account.close();
    Account saved = accountRepository.save(account);
    accountClosedNotifier.accountClosed(saved.getId());
    return saved;
  }
}
