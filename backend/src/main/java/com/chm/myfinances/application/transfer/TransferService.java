package com.chm.myfinances.application.transfer;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Transfer}: create/edit/delete/findById/findAll (F005 spec). New ids come
 * from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Coordinates against F003's {@link AccountRepository} to validate both accounts exist and are
 * open, and that they're not the same account - ordinary application-layer orchestration (ADR
 * 0004), not a domain-layer dependency: {@code domain/transfer} itself never imports {@code
 * domain.account}.
 */
@Service
public class TransferService {

  private final TransferRepository transferRepository;
  private final AccountRepository accountRepository;
  private final IdGenerator idGenerator;

  public TransferService(
      TransferRepository transferRepository,
      AccountRepository accountRepository,
      IdGenerator idGenerator) {
    this.transferRepository = transferRepository;
    this.accountRepository = accountRepository;
    this.idGenerator = idGenerator;
  }

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
    return transferRepository.save(transfer);
  }

  public Transfer findById(UUID id) {
    return transferRepository.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
  }

  public Page<Transfer> findAll(TransferFilter filter, Pageable pageable) {
    return transferRepository.findAll(filter, pageable);
  }

  public Transfer edit(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    Transfer transfer = findById(id);
    requireDifferentAccounts(fromAccountId, toAccountId);
    Account fromAccount = requireOpenAccount(fromAccountId);
    Account toAccount = requireOpenAccount(toAccountId);

    transfer.edit(
        date, fromAccount.getId(), toAccount.getId(), amount, description, additionalNotes);
    return transferRepository.save(transfer);
  }

  public void delete(UUID id) {
    if (!transferRepository.existsById(id)) {
      throw new TransferNotFoundException(id);
    }
    transferRepository.deleteById(id);
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
}
