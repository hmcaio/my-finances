package com.chm.myfinances.infrastructure.investmentholding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentholding.InvestmentHoldingHasHistoryException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.HasHoldingHistoryChecker;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real-database test of {@link RealHasHoldingHistoryChecker} for the F038 source (ADR 0033): a
 * holding that has only an opening position has history, so a hard delete is the intended 409 and
 * not a raw {@code investment_opening_positions.holding_id} foreign-key failure (a 500).
 */
@DatabaseIntegrationTest
class RealHasHoldingHistoryCheckerTest {

  @Autowired private HasHoldingHistoryChecker checker;
  @Autowired private InvestmentHoldingService holdingService;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentOpeningPositionRepository positionRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;

  private InvestmentHolding holding;

  @BeforeEach
  void setUp() {
    UUID brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker History Checker Test",
                AccountType.INVESTMENT)
            .getId();
    UUID productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    categoryRepository.findAll().get(0).getId(),
                    null,
                    "Product History Checker Test",
                    null))
            .getId();
    holding =
        holdingRepository.save(
            InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null));
  }

  private void givenAPosition() {
    positionRepository.save(
        InvestmentOpeningPosition.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 10, 1),
            BigDecimal.TEN,
            BigDecimal.ONE));
  }

  @Test
  void aHoldingWithNothingHasNoHistory() {
    assertThat(checker.hasHistory(holding.getId())).isFalse();
  }

  @Test
  void aHoldingWithOnlyAnOpeningPositionHasHistory() {
    givenAPosition();

    assertThat(checker.hasHistory(holding.getId())).isTrue();
  }

  @Test
  void hardDeletingAHoldingWithOnlyAnOpeningPositionIsAConflictNotAForeignKeyFailure() {
    givenAPosition();

    assertThatThrownBy(() -> holdingService.delete(holding.getId()))
        .isInstanceOf(InvestmentHoldingHasHistoryException.class);
  }

  @Test
  void onceThePositionIsDeletedTheHoldingCanBeHardDeleted() {
    givenAPosition();
    positionRepository.deleteById(
        positionRepository.findByHoldingId(holding.getId()).orElseThrow().getId());

    holdingService.delete(holding.getId());

    assertThat(holdingRepository.existsById(holding.getId())).isFalse();
  }
}
