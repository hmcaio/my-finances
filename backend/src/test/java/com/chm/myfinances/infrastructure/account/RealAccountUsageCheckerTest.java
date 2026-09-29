package com.chm.myfinances.infrastructure.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.account.AccountUsageChecker;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link RealAccountUsageChecker} against the real database (ADR 0017): each of the four
 * referencing tables makes an account "used", and an unrelated account stays free.
 */
@DatabaseIntegrationTest
class RealAccountUsageCheckerTest {

  @Autowired private AccountUsageChecker checker;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private TransferRepository transferRepository;
  @Autowired private RecurringTemplateRepository recurringTemplateRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;
  @Autowired private InvestmentHoldingRepository investmentHoldingRepository;

  private Account account;
  private Account other;

  @BeforeEach
  void setUp() {
    account =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Usage Checking");
    other = TestFixtures.checkingAccount(accountRepository, institutionRepository, "Usage Other");
  }

  @Test
  void aFreshAccountIsNotUsed() {
    assertThat(checker.isUsed(account.getId())).isFalse();
  }

  @Test
  void aTransactionMakesTheAccountUsed() {
    Category category =
        TestFixtures.category(categoryRepository, "Usage Expense Test", CategoryType.EXPENSE);
    PaymentMethod method = TestFixtures.paymentMethod(paymentMethodRepository, "Usage Card Test");
    transactionRepository.save(
        TransactionMother.expense()
            .withAccountId(account.getId())
            .withCategoryId(category.getId())
            .withPaymentMethodId(method.getId())
            .build());

    assertThat(checker.isUsed(account.getId())).isTrue();
    assertThat(checker.isUsed(other.getId())).isFalse();
  }

  @Test
  void aTransferOnTheSourceSideMakesTheAccountUsed() {
    transferRepository.save(
        TransferMother.transfer()
            .withFromAccountId(account.getId())
            .withToAccountId(other.getId())
            .build());

    assertThat(checker.isUsed(account.getId())).isTrue();
  }

  @Test
  void aTransferOnTheDestinationSideMakesTheAccountUsed() {
    transferRepository.save(
        TransferMother.transfer()
            .withFromAccountId(other.getId())
            .withToAccountId(account.getId())
            .build());

    assertThat(checker.isUsed(account.getId())).isTrue();
  }

  @Test
  void anActiveRecurringTemplateMakesTheAccountUsed() {
    Category category =
        TestFixtures.category(categoryRepository, "Usage Template Test", CategoryType.EXPENSE);
    recurringTemplateRepository.save(
        RecurringTemplate.create(UUID.randomUUID(), category.getId(), account.getId(), "Rent"));

    assertThat(checker.isUsed(account.getId())).isTrue();
    assertThat(checker.isUsed(other.getId())).isFalse();
  }

  @Test
  void anInactiveRecurringTemplateStillMakesTheAccountUsed() {
    Category category =
        TestFixtures.category(categoryRepository, "Usage Stopped Test", CategoryType.EXPENSE);
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), category.getId(), account.getId(), "Old rent");
    template.close();
    recurringTemplateRepository.save(template);

    assertThat(template.isActive()).isFalse();
    assertThat(checker.isUsed(account.getId())).isTrue();
  }

  @Test
  void aClosedInvestmentHoldingStillMakesTheAccountUsed() {
    Account broker =
        TestFixtures.account(
            accountRepository, institutionRepository, "Usage Broker", AccountType.INVESTMENT);
    InvestmentCategory category =
        investmentCategoryRepository.save(
            InvestmentCategory.create(UUID.randomUUID(), "Usage Invest Test"));
    InvestmentProduct product =
        investmentProductRepository.save(
            InvestmentProductMother.product()
                .withInvestmentCategoryId(category.getId())
                .withInvestmentSubcategoryId(null)
                .withName("Usage Product Test")
                .build());
    InvestmentHolding holding =
        InvestmentHolding.create(UUID.randomUUID(), product.getId(), broker.getId(), null);
    holding.close(LocalDate.of(2026, 1, 31));
    investmentHoldingRepository.save(holding);

    assertThat(checker.isUsed(broker.getId())).isTrue();
    assertThat(checker.isUsed(account.getId())).isFalse();
  }
}
