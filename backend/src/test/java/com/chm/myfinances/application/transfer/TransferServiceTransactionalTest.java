package com.chm.myfinances.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) that {@link
 * TransferService#create}'s {@code @Transactional} boundary rolls the transfer back when the
 * snapshot write that follows it (a trade's {@code resultingBalance}) fails - without it a buy
 * would be committed with no snapshot, and the caller would see a 500 implying nothing was saved.
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} - see {@code
 * RecurringTemplateServiceTransactionalTest}'s javadoc for why that would defeat the point. The
 * account/product fixtures stay behind (no delete port), so they get unique names; the transfer is
 * the very thing expected to be rolled back, so nothing else needs cleaning up.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferServiceTransactionalTest {

  @Autowired private TransferService transferService;
  @Autowired private TransferRepository transferRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  @MockitoSpyBean private InvestmentSnapshotRepository snapshotRepository;

  @Test
  void createRollsBackTheTransferWhenTheResultingBalanceSnapshotFails() {
    Account checking =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "Checking Trade Rollback Test",
            AccountType.CHECKING);
    Account broker =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "Broker Trade Rollback Test",
            AccountType.INVESTMENT);
    InvestmentProduct product =
        productRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                categoryRepository.findAll().get(0).getId(),
                null,
                "Product Trade Rollback Test",
                null));
    holdingRepository.save(
        InvestmentHolding.create(UUID.randomUUID(), product.getId(), broker.getId(), null));
    willThrow(new RuntimeException("simulated failure writing the snapshot"))
        .given(snapshotRepository)
        .save(any(InvestmentSnapshot.class));

    assertThatThrownBy(
            () ->
                transferService.create(
                    LocalDate.of(2026, 3, 15),
                    checking.getId(),
                    broker.getId(),
                    new BigDecimal("1000.00"),
                    "Buy",
                    null,
                    product.getId(),
                    null,
                    new BigDecimal("1000.00")))
        .isInstanceOf(RuntimeException.class);

    // The transfer, written first, must have rolled back with the failed snapshot.
    assertThat(
            transferRepository
                .findAll(
                    new TransferFilter(null, null, null, product.getId()), PageRequest.of(0, 10))
                .getTotalElements())
        .isZero();
    assertThat(transferRepository.existsByInvestmentProductId(product.getId())).isFalse();
  }
}
