package com.chm.myfinances.application.transaction;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.paymentmethod.PaymentMethodNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Transaction}: create/edit/delete/findById/findAll (F004 spec). New ids come
 * from the {@link IdGenerator} port (ADR 0005). {@code type} is always derived from the target
 * category's own (immutable, F002) type - never accepted as caller input - so it can never drift
 * from the category it's denormalized from.
 *
 * <p>Coordinates across three other aggregates' repository ports (category, account, payment
 * method) to validate foreign references exist and, for account, is open - this is ordinary
 * application-layer orchestration (ADR 0004), not a domain-layer dependency: {@code
 * domain/transaction} itself never imports {@code domain.account}/{@code domain.category} beyond
 * the shared {@code CategoryType} enum.
 */
@Service
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final AccountRepository accountRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final IdGenerator idGenerator;

  public TransactionService(
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      AccountRepository accountRepository,
      PaymentMethodRepository paymentMethodRepository,
      IdGenerator idGenerator) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.accountRepository = accountRepository;
    this.paymentMethodRepository = paymentMethodRepository;
    this.idGenerator = idGenerator;
  }

  public Transaction create(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes) {
    Category category = requireCategory(categoryId);
    Account account = requireOpenAccount(accountId);
    requirePaymentMethod(paymentMethodId);

    Transaction transaction =
        Transaction.create(
            idGenerator.newId(),
            date,
            amount,
            categoryId,
            category.getType(),
            account.getId(),
            paymentMethodId,
            null,
            description,
            additionalNotes);
    return transactionRepository.save(transaction);
  }

  public Transaction findById(UUID id) {
    return transactionRepository
        .findById(id)
        .orElseThrow(() -> new TransactionNotFoundException(id));
  }

  public Page<Transaction> findAll(TransactionFilter filter, Pageable pageable) {
    return transactionRepository.findAll(filter, pageable);
  }

  public Transaction edit(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes) {
    Transaction transaction = findById(id);
    Category category = requireCategory(categoryId);
    Account account = requireOpenAccount(accountId);
    requirePaymentMethod(paymentMethodId);

    transaction.edit(
        date,
        amount,
        categoryId,
        category.getType(),
        account.getId(),
        paymentMethodId,
        description,
        additionalNotes);
    return transactionRepository.save(transaction);
  }

  public void delete(UUID id) {
    if (!transactionRepository.existsById(id)) {
      throw new TransactionNotFoundException(id);
    }
    transactionRepository.deleteById(id);
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
    account.requireOpen();
    return account;
  }
}
