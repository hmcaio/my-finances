package com.chm.myfinances.infrastructure.persistence.investmentopeningposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link InvestmentOpeningPositionRepositoryAdapter} and the
 * {@code findEarliestDateByHolding} query of {@code TransferTradeLineRepositoryAdapter} (F038, ADR
 * 0033): hits a real, ephemeral Postgres via Testcontainers (ADR 0010), so {@code V27} runs for
 * real too.
 */
@DatabaseIntegrationTest
class InvestmentOpeningPositionRepositoryAdapterTest {

  @Autowired private InvestmentOpeningPositionRepository positionRepository;
  @Autowired private TransferTradeLineRepository tradeLineRepository;
  @Autowired private TransferRepository transferRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private EntityManager entityManager;

  private UUID cashId;
  private UUID brokerId;
  private UUID otherBrokerId;
  private UUID productId;
  private UUID holdingId;
  private UUID otherHoldingId;

  @BeforeEach
  void setUp() {
    cashId =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Cash Position Repo")
            .getId();
    brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Position Repo Test",
                AccountType.INVESTMENT)
            .getId();
    otherBrokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Other Broker Position Repo Test",
                AccountType.INVESTMENT)
            .getId();
    productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    categoryRepository.findAll().get(0).getId(),
                    null,
                    "Product Position Repo Test",
                    null))
            .getId();
    holdingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
            .getId();
    otherHoldingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, otherBrokerId, null))
            .getId();
  }

  private InvestmentOpeningPosition position(UUID holding, LocalDate asOf) {
    return InvestmentOpeningPosition.create(
        UUID.randomUUID(), holding, asOf, new BigDecimal("40.12345678"), new BigDecimal("150.5"));
  }

  @Test
  void savesAndReloadsAPositionWithEightDecimalQuantity() {
    InvestmentOpeningPosition saved =
        positionRepository.save(position(holdingId, LocalDate.of(2026, 10, 1)));
    entityManager.flush();
    entityManager.clear();

    InvestmentOpeningPosition reloaded =
        positionRepository.findByHoldingId(holdingId).orElseThrow();

    assertThat(reloaded.getId()).isEqualTo(saved.getId());
    assertThat(reloaded.getAsOfDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(reloaded.getQuantity()).isEqualByComparingTo("40.12345678");
    assertThat(reloaded.getUnitPrice()).isEqualByComparingTo("150.5");
  }

  @Test
  void editingUpdatesTheSameRow() {
    InvestmentOpeningPosition saved =
        positionRepository.save(position(holdingId, LocalDate.of(2026, 10, 1)));
    entityManager.flush();
    entityManager.clear();

    InvestmentOpeningPosition loaded = positionRepository.findByHoldingId(holdingId).orElseThrow();
    loaded.edit(LocalDate.of(2026, 9, 1), new BigDecimal("5"), new BigDecimal("2"));
    positionRepository.save(loaded);
    entityManager.flush();
    entityManager.clear();

    assertThat(positionRepository.findAll())
        .filteredOn(p -> p.getId().equals(saved.getId()))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.getAsOfDate()).isEqualTo(LocalDate.of(2026, 9, 1));
              assertThat(p.getQuantity()).isEqualByComparingTo("5");
            });
  }

  @Test
  void aSecondPositionForTheSameHoldingViolatesTheUniqueConstraint() {
    positionRepository.save(position(holdingId, LocalDate.of(2026, 10, 1)));
    entityManager.flush();

    assertThatThrownBy(
            () -> {
              positionRepository.save(position(holdingId, LocalDate.of(2026, 9, 1)));
              entityManager.flush();
            })
        .isInstanceOfAny(
            org.springframework.dao.DataIntegrityViolationException.class,
            jakarta.persistence.PersistenceException.class);
  }

  @Test
  void findByHoldingIdsReturnsOnlyTheRequestedHoldingsAndHandlesAnEmptyInput() {
    positionRepository.save(position(holdingId, LocalDate.of(2026, 10, 1)));
    positionRepository.save(position(otherHoldingId, LocalDate.of(2026, 10, 2)));

    assertThat(positionRepository.findByHoldingIds(List.of(holdingId)))
        .extracting(InvestmentOpeningPosition::getHoldingId)
        .containsExactly(holdingId);
    assertThat(positionRepository.findByHoldingIds(List.of())).isEmpty();
  }

  @Test
  void existsByHoldingIdAndDeleteByIdReflectPersistedState() {
    assertThat(positionRepository.existsByHoldingId(holdingId)).isFalse();
    InvestmentOpeningPosition saved =
        positionRepository.save(position(holdingId, LocalDate.of(2026, 10, 1)));
    assertThat(positionRepository.existsByHoldingId(holdingId)).isTrue();
    assertThat(positionRepository.existsByHoldingId(otherHoldingId)).isFalse();

    positionRepository.deleteById(saved.getId());
    entityManager.flush();

    assertThat(positionRepository.findByHoldingId(holdingId)).isEmpty();
  }

  @Test
  void findEarliestDateByHoldingIsScopedToTheProductAndTheAccountOnEitherSide() {
    assertThat(tradeLineRepository.findEarliestDateByHolding(productId, brokerId)).isEmpty();

    saveTrade(LocalDate.of(2026, 6, 15), cashId, brokerId, TradeSide.BUY);
    saveTrade(LocalDate.of(2026, 6, 1), cashId, brokerId, TradeSide.BUY);
    saveTrade(LocalDate.of(2026, 7, 1), cashId, brokerId, TradeSide.SELL);
    saveTrade(LocalDate.of(2026, 1, 1), cashId, otherBrokerId, TradeSide.BUY);

    assertThat(tradeLineRepository.findEarliestDateByHolding(productId, brokerId))
        .contains(LocalDate.of(2026, 6, 1));
    assertThat(tradeLineRepository.findEarliestDateByHolding(productId, otherBrokerId))
        .contains(LocalDate.of(2026, 1, 1));
    assertThat(tradeLineRepository.findEarliestDateByHolding(UUID.randomUUID(), brokerId))
        .isEmpty();
  }

  private void saveTrade(LocalDate date, UUID cash, UUID investment, TradeSide side) {
    transferRepository.save(
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            date,
            cash,
            investment,
            "Trade",
            null,
            BigDecimal.ZERO,
            TradeConfirmation.of(
                List.of(
                    new TradeConfirmationLine(
                        productId, side, BigDecimal.TEN, BigDecimal.ONE, null, false)))));
  }
}
