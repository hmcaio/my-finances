package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Object-mother helpers for real-DB tests (issue #31, B5) that just need *an* {@link Account},
 * {@link Category} or {@link PaymentMethod} to exist, persisted through the real, Spring-managed
 * repository ports (not a {@code Fake*Repository}). Extracted from the near-identical {@code
 * persistAccount}/{@code persistCategory}/{@code persistPaymentMethod} helpers duplicated across
 * {@code TransactionRepositoryAdapterTest}, {@code TransferRepositoryAdapterTest}, {@code
 * TransferControllerTest} and others.
 *
 * <p>Defaults match what those helpers already used: opening balance {@link BigDecimal#ZERO} and
 * opening date "today" ({@link LocalDate#now()} - these tests don't depend on a fixed clock, so a
 * plain {@code now()} is fine, same as pre-existing call sites). The institution is always the
 * built-in one via {@link TestInstitutions#builtInId(InstitutionRepository)}.
 *
 * <p>A call site whose fixture has a genuinely different shape (a closed account, a specific
 * opening balance the test asserts on, an {@code INVESTMENT} account) should keep its own inline
 * {@code *.create(...)} call instead of forcing it through these helpers.
 */
public final class TestFixtures {

  private TestFixtures() {}

  /** Persists and returns an open {@code CHECKING} account with the given name. */
  public static Account checkingAccount(
      AccountRepository accountRepository,
      InstitutionRepository institutionRepository,
      String name) {
    return account(accountRepository, institutionRepository, name, AccountType.CHECKING);
  }

  /**
   * Persists and returns an open account of the given type with the given name. For every type
   * except {@code INVESTMENT} the opening balance defaults to {@link BigDecimal#ZERO} and the
   * opening date to today; an {@code INVESTMENT} account has neither (ADR 0012).
   */
  public static Account account(
      AccountRepository accountRepository,
      InstitutionRepository institutionRepository,
      String name,
      AccountType type) {
    BigDecimal openingBalance = type == AccountType.INVESTMENT ? null : BigDecimal.ZERO;
    LocalDate openingBalanceDate = type == AccountType.INVESTMENT ? null : LocalDate.now();
    return accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            name,
            TestInstitutions.builtInId(institutionRepository),
            type,
            openingBalance,
            openingBalanceDate));
  }

  /** Persists and returns a {@link Category} of the given type with the given name. */
  public static Category category(
      CategoryRepository categoryRepository, String name, CategoryType type) {
    return categoryRepository.save(Category.create(UUID.randomUUID(), name, type));
  }

  /** Persists and returns a {@link PaymentMethod} with the given name. */
  public static PaymentMethod paymentMethod(
      PaymentMethodRepository paymentMethodRepository, String name) {
    return paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), name));
  }
}
