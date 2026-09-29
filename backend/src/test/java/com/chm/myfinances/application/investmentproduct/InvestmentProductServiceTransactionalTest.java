package com.chm.myfinances.application.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.AbstractTransactionalBoundaryTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) that {@link
 * InvestmentProductService#create}'s {@code @Transactional} boundary rolls the product insert back
 * when the first-holding write that follows it fails - without it a failure would leave a
 * holding-less product committed (F022 spec, backend CLAUDE.md "Transactions" rule).
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} - see {@code
 * RecurringTemplateServiceTransactionalTest}'s javadoc for why that would defeat the point. The
 * account fixture stays behind (no delete port), so it gets a unique name; the product is the very
 * thing expected to be rolled back, so nothing else needs cleaning up.
 */
class InvestmentProductServiceTransactionalTest extends AbstractTransactionalBoundaryTest {

  @Autowired private InvestmentProductService productService;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;

  @Test
  void createRollsBackTheProductWhenTheFirstHoldingWriteFails() {
    Account broker =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "Broker Product Rollback Test",
            AccountType.INVESTMENT);
    willThrow(new RuntimeException("simulated failure writing the holding"))
        .given(holdingRepository)
        .save(any());

    assertThatThrownBy(
            () ->
                productService.create(
                    broker.getId(),
                    categoryRepository.findAll().get(0).getId(),
                    null,
                    "Product Rollback Test",
                    null))
        .isInstanceOf(RuntimeException.class);

    // The product, written first, must have rolled back with the failed holding write.
    assertThat(productRepository.existsByName("Product Rollback Test")).isFalse();
  }
}
