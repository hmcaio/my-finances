package com.chm.myfinances.infrastructure.persistence.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Persistence-layer integration test for {@link TransferRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010), so Flyway's {@code V7__transfers.sql} runs for real too,
 * FK/check constraints included. Same {@code @SpringBootTest} +
 * {@code @Import(TestcontainersConfiguration.class)} + {@code @Transactional} pattern as F004's
 * {@code TransactionRepositoryAdapterTest}.
 *
 * <p>{@code transfers.from_account_id}/{@code to_account_id} are real FKs, so every test here
 * persists real {@link Account}s first via {@link AccountRepository} rather than random UUIDs.
 */
@DatabaseIntegrationTest
class TransferRepositoryAdapterTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private TransferRepository transferRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;

  private UUID checkingId;
  private UUID savingsId;
  private UUID creditCardId;
  private UUID brokerId;
  private UUID productId;

  @BeforeEach
  void setUp() {
    checkingId = persistAccount("Checking", AccountType.CHECKING).getId();
    savingsId = persistAccount("Savings", AccountType.SAVINGS).getId();
    creditCardId = persistAccount("Credit Card", AccountType.CREDIT_CARD).getId();
    brokerId = persistAccount("Broker Transfer Repo Test", AccountType.INVESTMENT).getId();
    productId =
        investmentProductRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    investmentCategoryRepository.findAll().get(0).getId(),
                    null,
                    "Product Transfer Repo Test",
                    null))
            .getId();
  }

  private Account persistAccount(String name, AccountType type) {
    return TestFixtures.account(accountRepository, institutionRepository, name, type);
  }

  private Transfer newTransfer(LocalDate date, UUID fromAccountId, UUID toAccountId) {
    return Transfer.create(
        UUID.randomUUID(), date, fromAccountId, toAccountId, BigDecimal.TEN, "Test transfer", null);
  }

  @Test
  void savesAndReloadsATransfer() {
    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            checkingId,
            creditCardId,
            new BigDecimal("42.50"),
            "Credit card payment",
            "Paid the statement balance");

    transferRepository.save(transfer);

    Optional<Transfer> reloaded = transferRepository.findById(transfer.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    assertThat(reloaded.get().getFromAccountId()).isEqualTo(checkingId);
    assertThat(reloaded.get().getToAccountId()).isEqualTo(creditCardId);
    assertThat(reloaded.get().getAmount()).isEqualByComparingTo("42.50");
    assertThat(reloaded.get().getDescription()).isEqualTo("Credit card payment");
    assertThat(reloaded.get().getAdditionalNotes()).isEqualTo("Paid the statement balance");
  }

  @Test
  void editPersists() {
    Transfer transfer = newTransfer(LocalDate.of(2026, 1, 1), checkingId, creditCardId);
    transferRepository.save(transfer);

    transfer.edit(
        LocalDate.of(2026, 2, 2), savingsId, creditCardId, new BigDecimal("20.00"), "Edited", "n");
    transferRepository.save(transfer);

    Optional<Transfer> reloaded = transferRepository.findById(transfer.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(reloaded.get().getFromAccountId()).isEqualTo(savingsId);
    assertThat(reloaded.get().getAmount()).isEqualByComparingTo("20.00");
    assertThat(reloaded.get().getDescription()).isEqualTo("Edited");
  }

  @Test
  void deleteByIdRemovesTheTransfer() {
    Transfer transfer = newTransfer(LocalDate.now(), checkingId, creditCardId);
    transferRepository.save(transfer);

    transferRepository.deleteById(transfer.getId());

    assertThat(transferRepository.findById(transfer.getId())).isEmpty();
  }

  @Test
  void existsByIdReflectsPersistedState() {
    Transfer transfer = newTransfer(LocalDate.now(), checkingId, creditCardId);

    assertThat(transferRepository.existsById(transfer.getId())).isFalse();

    transferRepository.save(transfer);

    assertThat(transferRepository.existsById(transfer.getId())).isTrue();
  }

  @Test
  void findByAccountIdOnOrBeforeMatchesEitherSideAndExcludesLaterDates() {
    Transfer asSource = newTransfer(LocalDate.of(2026, 1, 10), checkingId, creditCardId);
    Transfer asDestination = newTransfer(LocalDate.of(2026, 1, 12), savingsId, checkingId);
    Transfer afterAsOf = newTransfer(LocalDate.of(2026, 1, 20), checkingId, creditCardId);
    Transfer unrelated = newTransfer(LocalDate.of(2026, 1, 10), savingsId, creditCardId);
    transferRepository.save(asSource);
    transferRepository.save(asDestination);
    transferRepository.save(afterAsOf);
    transferRepository.save(unrelated);

    var result =
        transferRepository.findByAccountIdOnOrBefore(checkingId, LocalDate.of(2026, 1, 15));

    assertThat(result)
        .extracting(Transfer::getId)
        .containsExactlyInAnyOrder(asSource.getId(), asDestination.getId());
  }

  @Test
  void findDistinctDatesBetweenReturnsEachDateOnceWithinTheInclusiveRange() {
    transferRepository.save(newTransfer(LocalDate.of(1990, 1, 9), checkingId, creditCardId));
    transferRepository.save(newTransfer(LocalDate.of(1990, 1, 10), checkingId, creditCardId));
    transferRepository.save(newTransfer(LocalDate.of(1990, 1, 10), savingsId, checkingId));
    transferRepository.save(newTransfer(LocalDate.of(1990, 1, 20), checkingId, creditCardId));
    transferRepository.save(newTransfer(LocalDate.of(1990, 1, 21), checkingId, creditCardId));

    assertThat(
            transferRepository.findDistinctDatesBetween(
                LocalDate.of(1990, 1, 10), LocalDate.of(1990, 1, 20)))
        .containsExactlyInAnyOrder(LocalDate.of(1990, 1, 10), LocalDate.of(1990, 1, 20));
  }

  @Test
  void findAllFiltersByAccountIdOnEitherSideAndDateRangeAndPaginates() {
    Transfer matchingAsSource = newTransfer(LocalDate.of(2026, 2, 1), checkingId, creditCardId);
    Transfer matchingAsDestination = newTransfer(LocalDate.of(2026, 2, 15), savingsId, checkingId);
    Transfer notMatchingAccount = newTransfer(LocalDate.of(2026, 2, 10), savingsId, creditCardId);
    Transfer outOfDateRange = newTransfer(LocalDate.of(2026, 3, 1), checkingId, creditCardId);
    transferRepository.save(matchingAsSource);
    transferRepository.save(matchingAsDestination);
    transferRepository.save(notMatchingAccount);
    transferRepository.save(outOfDateRange);

    TransferFilter filter =
        new TransferFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), checkingId, null);

    Page<Transfer> page = transferRepository.findAll(filter, PageRequest.of(0, 1));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getTotalPages()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(1);
  }

  @Test
  void findAllWithNoFilterReturnsEverything() {
    transferRepository.save(newTransfer(LocalDate.now(), checkingId, creditCardId));
    transferRepository.save(newTransfer(LocalDate.now(), savingsId, checkingId));

    Page<Transfer> page = transferRepository.findAll(TransferFilter.none(), PageRequest.of(0, 20));

    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
  }

  private Transfer newTrade(
      LocalDate date,
      UUID cashAccountId,
      UUID investmentAccountId,
      TradeSide side,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal taxes) {
    return Transfer.createTradeConfirmation(
        UUID.randomUUID(),
        date,
        cashAccountId,
        investmentAccountId,
        "Trade",
        null,
        taxes,
        TradeConfirmation.of(
            List.of(new TradeConfirmationLine(productId, side, quantity, unitPrice, null, false))));
  }

  @Test
  void aTaggedTradeRoundTripsWithEightDecimalQuantityAndPrice() {
    Transfer trade =
        newTrade(
            LocalDate.of(2026, 3, 10),
            checkingId,
            brokerId,
            TradeSide.BUY,
            new BigDecimal("0.12345678"),
            new BigDecimal("250000.87654321"),
            new BigDecimal("5.25"));

    transferRepository.save(trade);
    entityManager.flush();
    entityManager.clear();

    Transfer reloaded = transferRepository.findById(trade.getId()).orElseThrow();
    TradeConfirmationLine line = reloaded.getTradeConfirmation().orElseThrow().getLines().get(0);
    assertThat(line.productId()).isEqualTo(productId);
    assertThat(line.quantity()).isEqualByComparingTo("0.12345678");
    assertThat(line.unitPrice()).isEqualByComparingTo("250000.87654321");
    assertThat(reloaded.getTaxes()).isEqualByComparingTo("5.25");
  }

  @Test
  void aPlainTransferReloadsWithNoTaxesAndNoTradeConfirmation() {
    Transfer plain = newTransfer(LocalDate.now(), checkingId, savingsId);
    transferRepository.save(plain);
    entityManager.flush();
    entityManager.clear();

    Transfer reloaded = transferRepository.findById(plain.getId()).orElseThrow();

    assertThat(reloaded.getTaxes()).isNull();
    assertThat(reloaded.getTradeConfirmation()).isEmpty();
  }

  @Test
  void editingATradeConfirmationPersistsItsLinesAndClearingItWorks() {
    Transfer transfer = newTransfer(LocalDate.of(2026, 1, 1), checkingId, brokerId);
    transferRepository.save(transfer);

    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                new TradeConfirmationLine(
                    productId, TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, null, false)));
    transfer.editTradeConfirmation(
        LocalDate.of(2026, 1, 2),
        checkingId,
        brokerId,
        "Now a buy",
        null,
        BigDecimal.ZERO,
        confirmation);
    transferRepository.save(transfer);
    entityManager.flush();
    entityManager.clear();
    Transfer tagged = transferRepository.findById(transfer.getId()).orElseThrow();
    assertThat(tagged.getTradeConfirmation()).isPresent();
    assertThat(tagged.getTradeConfirmation().orElseThrow().getLines().get(0).quantity())
        .isEqualByComparingTo("1");

    tagged.edit(
        LocalDate.of(2026, 1, 3), checkingId, savingsId, BigDecimal.TEN, "Plain again", null);
    transferRepository.save(tagged);
    entityManager.flush();
    entityManager.clear();
    Transfer untagged = transferRepository.findById(transfer.getId()).orElseThrow();
    assertThat(untagged.getTaxes()).isNull();
    assertThat(untagged.getTradeConfirmation()).isEmpty();
  }

  @Test
  void findAllFiltersByInvestmentProductIdViaTheLinesJoin() {
    Transfer buy =
        newTrade(
            LocalDate.of(2026, 3, 1),
            checkingId,
            brokerId,
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO);
    transferRepository.save(buy);
    transferRepository.save(newTransfer(LocalDate.of(2026, 3, 2), checkingId, savingsId));

    Page<Transfer> page =
        transferRepository.findAll(
            new TransferFilter(null, null, null, productId), PageRequest.of(0, 20));

    assertThat(page.getContent()).extracting(Transfer::getId).containsExactly(buy.getId());
  }

  @Test
  void deletingATransferRemovesItsTradeLines() {
    Transfer buy =
        newTrade(
            LocalDate.of(2026, 3, 1),
            checkingId,
            brokerId,
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO);
    transferRepository.save(buy);

    transferRepository.deleteById(buy.getId());

    assertThat(transferRepository.findById(buy.getId())).isEmpty();
  }
}
