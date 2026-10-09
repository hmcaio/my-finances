package com.chm.myfinances.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingClosedException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link TransferService}'s trade-confirmation rules (F027 spec, ADR
 * 0024, superseding F009/F022's single-product shape), written first (ADR 0004): a confirmation is
 * a transfer between a cash account and an {@code INVESTMENT} account, carrying one or more lines
 * each tagged with a product for which an open holding must already exist in that account (holdings
 * are created explicitly - a trade never creates one). Mixed BUY/SELL lines and duplicate-product
 * lines (partial fills) are accepted; per-line {@code resultingBalance} writes a same-day snapshot,
 * per-line {@code closeHolding} closes that holding - all inside the same {@code @Transactional}
 * use case as the {@link Transfer} write.
 */
class TransferServiceInvestmentTest {

  private static final LocalDate DATE = LocalDate.of(2026, 3, 15);

  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final TransferService service =
      new TransferService(
          transferRepository,
          accountRepository,
          holdingRepository,
          new InvestmentSnapshotService(
              snapshotRepository, holdingRepository, idGenerator, new AuditRecorder(auditLog)),
          new InvestmentHoldingService(
              holdingRepository,
              productRepository,
              accountRepository,
              id -> false,
              new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
              idGenerator,
              Clock.systemUTC(),
              new AuditRecorder(auditLog)),
          idGenerator,
          new AuditRecorder(auditLog));

  private Account checking;
  private Account savings;
  private Account broker;
  private Account otherBroker;
  private InvestmentProduct product;
  private InvestmentHolding holding;
  private InvestmentProduct otherProduct;
  private InvestmentHolding otherHolding;
  private InvestmentProduct otherBrokerProduct;

  @BeforeEach
  void setUp() {
    checking = accountRepository.save(AccountMother.checking().build());
    savings = accountRepository.save(AccountMother.savings().build());
    broker = accountRepository.save(AccountMother.investment().build());
    otherBroker =
        accountRepository.save(AccountMother.investment().withName("Other broker").build());
    product = productRepository.save(InvestmentProductMother.product().build());
    holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(broker.getId())
                .build());
    otherProduct = productRepository.save(InvestmentProductMother.product().withName("B").build());
    otherHolding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(otherProduct.getId())
                .withAccountId(broker.getId())
                .build());
    otherBrokerProduct =
        productRepository.save(InvestmentProductMother.product().withName("Foreign").build());
    // otherBrokerProduct has no holding anywhere - used to test the "no holding" 404.
  }

  private static TradeConfirmationLine buyLine(UUID productId, String quantity, String unitPrice) {
    return new TradeConfirmationLine(
        productId, TradeSide.BUY, new BigDecimal(quantity), new BigDecimal(unitPrice), null, false);
  }

  private static TradeConfirmationLine sellLine(UUID productId, String quantity, String unitPrice) {
    return new TradeConfirmationLine(
        productId,
        TradeSide.SELL,
        new BigDecimal(quantity),
        new BigDecimal(unitPrice),
        null,
        false);
  }

  private Transfer createWith(List<TradeConfirmationLine> lines) {
    return service.createTradeConfirmation(
        DATE, checking.getId(), broker.getId(), "Trade", null, BigDecimal.ZERO, lines);
  }

  private Transfer plainTransfer() {
    return service.create(
        DATE, checking.getId(), savings.getId(), BigDecimal.TEN, "Transfer", null);
  }

  // --- create -----------------------------------------------------------------------------------

  @Test
  void aSingleBuyLineIsATransferIntoTheInvestmentAccount() {
    Transfer created = createWith(List.of(buyLine(product.getId(), "10", "100.00")));

    assertThat(created.getFromAccountId()).isEqualTo(checking.getId());
    assertThat(created.getToAccountId()).isEqualTo(broker.getId());
    assertThat(created.getAmount()).isEqualByComparingTo("1000.00");
    assertThat(created.getTradeConfirmation()).isPresent();
  }

  @Test
  void aSingleSellLineIsATransferOutOfTheInvestmentAccount() {
    Transfer created = createWith(List.of(sellLine(product.getId(), "10", "100.00")));

    assertThat(created.getFromAccountId()).isEqualTo(broker.getId());
    assertThat(created.getToAccountId()).isEqualTo(checking.getId());
  }

  @Test
  void mixedBuyAndSellLinesAcrossDifferentProductsAreAccepted() {
    Transfer created =
        createWith(
            List.of(
                buyLine(product.getId(), "10", "100.00"),
                sellLine(otherProduct.getId(), "5", "50.00")));

    assertThat(created.getTradeConfirmation().orElseThrow().getLines()).hasSize(2);
  }

  @Test
  void duplicateProductLinesAsPartialFillsAreAccepted() {
    Transfer created =
        createWith(
            List.of(
                buyLine(product.getId(), "10", "100.00"), buyLine(product.getId(), "5", "101.00")));

    assertThat(created.getTradeConfirmation().orElseThrow().getLines()).hasSize(2);
    assertThat(created.getAmount()).isEqualByComparingTo("1505.00");
  }

  @Test
  void createRejectsAProductWithNoHoldingInThatAccount() {
    assertThatThrownBy(() -> createWith(List.of(buyLine(otherBrokerProduct.getId(), "1", "10.00"))))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
    assertThat(transferRepository.findAll()).isEmpty();
  }

  @Test
  void createRejectsAnInvestmentAccountThatIsNotActuallyInvestment() {
    assertThatThrownBy(
            () ->
                service.createTradeConfirmation(
                    DATE,
                    checking.getId(),
                    savings.getId(),
                    "Trade",
                    null,
                    BigDecimal.ZERO,
                    List.of(buyLine(product.getId(), "1", "10.00"))))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void createRejectsTwoInvestmentAccounts() {
    assertThatThrownBy(
            () ->
                service.createTradeConfirmation(
                    DATE,
                    broker.getId(),
                    otherBroker.getId(),
                    "Move",
                    null,
                    BigDecimal.ZERO,
                    List.of(buyLine(product.getId(), "1", "10.00"))))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void createRejectsAClosedHolding() {
    holding.close(LocalDate.of(2026, 3, 1));
    holdingRepository.save(holding);

    assertThatThrownBy(() -> createWith(List.of(buyLine(product.getId(), "1", "10.00"))))
        .isInstanceOf(InvestmentHoldingClosedException.class);
  }

  @Test
  void createRejectsAnUnknownProduct() {
    assertThatThrownBy(() -> createWith(List.of(buyLine(UUID.randomUUID(), "1", "10.00"))))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void createRejectsAClosedInvestmentAccount() {
    broker.close(LocalDate.of(2026, 3, 1));
    accountRepository.save(broker);

    assertThatThrownBy(() -> createWith(List.of(buyLine(product.getId(), "1", "10.00"))))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void createRejectsAnExactZeroNetSettlement() {
    assertThatThrownBy(
            () ->
                createWith(
                    List.of(
                        buyLine(product.getId(), "10", "100.00"),
                        sellLine(otherProduct.getId(), "10", "100.00"))))
        .isInstanceOf(InvalidTradeConfirmationException.class);
    assertThat(transferRepository.findAll()).isEmpty();
  }

  @Test
  void aPlainTransferStillNeedsNoTradeConfirmation() {
    Transfer created = plainTransfer();

    assertThat(created.getTradeConfirmation()).isEmpty();
  }

  // --- edit -------------------------------------------------------------------------------------

  @Test
  void editTradeConfirmationReplacesTheLinesAndRecomputesAmountAndDirection() {
    Transfer created = createWith(List.of(buyLine(product.getId(), "10", "100.00")));

    Transfer edited =
        service.editTradeConfirmation(
            created.getId(),
            DATE,
            checking.getId(),
            broker.getId(),
            "Edited",
            null,
            BigDecimal.ZERO,
            List.of(sellLine(product.getId(), "10", "100.00")));

    assertThat(edited.getFromAccountId()).isEqualTo(broker.getId());
    assertThat(edited.getToAccountId()).isEqualTo(checking.getId());
  }

  @Test
  void editRejectsAProductWithNoHoldingInThatAccount() {
    Transfer created = createWith(List.of(buyLine(product.getId(), "10", "100.00")));

    assertThatThrownBy(
            () ->
                service.editTradeConfirmation(
                    created.getId(),
                    DATE,
                    checking.getId(),
                    broker.getId(),
                    "Edited",
                    null,
                    BigDecimal.ZERO,
                    List.of(buyLine(otherBrokerProduct.getId(), "1", "10.00"))))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void editRejectsAClosedHolding() {
    Transfer created = createWith(List.of(buyLine(product.getId(), "10", "100.00")));
    holding.close(LocalDate.of(2026, 3, 20));
    holdingRepository.save(holding);

    assertThatThrownBy(
            () ->
                service.editTradeConfirmation(
                    created.getId(),
                    DATE,
                    checking.getId(),
                    broker.getId(),
                    "Edited",
                    null,
                    BigDecimal.ZERO,
                    List.of(buyLine(product.getId(), "1", "10.00"))))
        .isInstanceOf(InvestmentHoldingClosedException.class);
  }

  @Test
  void editCanClearATradeConfirmationBackToAPlainTransfer() {
    Transfer created = createWith(List.of(buyLine(product.getId(), "10", "100.00")));

    Transfer edited =
        service.edit(
            created.getId(),
            DATE,
            checking.getId(),
            savings.getId(),
            BigDecimal.TEN,
            "Plain",
            null);

    assertThat(edited.getTradeConfirmation()).isEmpty();
  }

  // --- resulting balance / close holding (per line) ---------------------------------------------

  @Test
  void resultingBalanceOnOneLineWritesASnapshotForItsHoldingOnly() {
    TradeConfirmationLine withBalance =
        new TradeConfirmationLine(
            product.getId(),
            TradeSide.BUY,
            BigDecimal.TEN,
            BigDecimal.TEN,
            new BigDecimal("2500.00"),
            false);
    createWith(List.of(withBalance));

    List<InvestmentSnapshot> snapshots = snapshotRepository.findByHoldingId(holding.getId());
    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.get(0).getDate()).isEqualTo(DATE);
    assertThat(snapshots.get(0).getBalance()).isEqualByComparingTo("2500.00");
    assertThat(snapshotRepository.findByHoldingId(otherHolding.getId())).isEmpty();
  }

  @Test
  void resultingBalancesOnTwoLinesForDifferentProductsWriteBothSnapshots() {
    TradeConfirmationLine a =
        new TradeConfirmationLine(
            product.getId(),
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.TEN,
            new BigDecimal("100.00"),
            false);
    TradeConfirmationLine b =
        new TradeConfirmationLine(
            otherProduct.getId(),
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.TEN,
            new BigDecimal("200.00"),
            false);

    createWith(List.of(a, b));

    assertThat(snapshotRepository.findByHoldingId(holding.getId()))
        .singleElement()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("100.00"));
    assertThat(snapshotRepository.findByHoldingId(otherHolding.getId()))
        .singleElement()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("200.00"));
  }

  @Test
  void closeHoldingClosesTheHoldingWhenItsLatestSnapshotIsZero() {
    TradeConfirmationLine sellAndClose =
        new TradeConfirmationLine(
            product.getId(), TradeSide.SELL, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, true);

    createWith(List.of(sellAndClose));

    assertThat(holdingRepository.findById(holding.getId()).orElseThrow().isClosed()).isTrue();
  }

  @Test
  void noResultingBalanceWritesNoSnapshot() {
    createWith(List.of(buyLine(product.getId(), "10", "100.00")));

    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void aRejectedTradeWritesNoSnapshot() {
    assertThatThrownBy(
            () ->
                createWith(
                    List.of(
                        new TradeConfirmationLine(
                            otherBrokerProduct.getId(),
                            TradeSide.BUY,
                            BigDecimal.ONE,
                            BigDecimal.ONE,
                            BigDecimal.ONE,
                            false))))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);

    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  // --- multi-holding (F022) ---------------------------------------------------------------------

  @Test
  void theSameProductCanBeTradedIndependentlyAtTwoHoldings() {
    InvestmentHolding otherBrokerHolding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(otherBroker.getId())
                .build());

    Transfer atBroker =
        service.createTradeConfirmation(
            DATE,
            checking.getId(),
            broker.getId(),
            "Buy",
            null,
            BigDecimal.ZERO,
            List.of(
                new TradeConfirmationLine(
                    product.getId(),
                    TradeSide.BUY,
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    new BigDecimal("1000.00"),
                    false)));
    Transfer atOtherBroker =
        service.createTradeConfirmation(
            DATE,
            checking.getId(),
            otherBroker.getId(),
            "Buy elsewhere",
            null,
            BigDecimal.ZERO,
            List.of(
                new TradeConfirmationLine(
                    product.getId(),
                    TradeSide.BUY,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    new BigDecimal("500.00"),
                    false)));

    assertThat(snapshotRepository.findByHoldingId(holding.getId()))
        .singleElement()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("1000.00"));
    assertThat(snapshotRepository.findByHoldingId(otherBrokerHolding.getId()))
        .singleElement()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("500.00"));
    assertThat(atBroker.getId()).isNotEqualTo(atOtherBroker.getId());
  }
}
