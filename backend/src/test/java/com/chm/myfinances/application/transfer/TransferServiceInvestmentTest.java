package com.chm.myfinances.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentholding.InvestmentHoldingClosedException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link TransferService}'s investment rules (F009 spec, rewired onto
 * holdings by F022/ADR 0020), written first (ADR 0004): a buy/sell is a transfer between a cash
 * account and an {@code INVESTMENT} account, tagged with a product for which an open holding must
 * already exist in that account (holdings are created explicitly - a trade never creates one).
 * Every case depends on persisted state, so each is its own 404/409 exception. Applied on create
 * and edit; an optional {@code resultingBalance} on create also writes a snapshot.
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
  private final TransferService service =
      new TransferService(
          transferRepository,
          accountRepository,
          holdingRepository,
          new InvestmentSnapshotService(snapshotRepository, holdingRepository, idGenerator),
          idGenerator);

  private Account checking;
  private Account savings;
  private Account broker;
  private Account otherBroker;
  private InvestmentProduct product;
  private InvestmentHolding holding;
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
    otherBrokerProduct =
        productRepository.save(InvestmentProductMother.product().withName("Foreign").build());
    // otherBrokerProduct has no holding anywhere - used to test the "no holding" 404.
  }

  private Transfer buy(
      UUID productId, InvestmentTradeDetails details, BigDecimal resultingBalance) {
    return service.create(
        DATE,
        checking.getId(),
        broker.getId(),
        new BigDecimal("1005.00"),
        "Buy",
        null,
        productId,
        details,
        resultingBalance);
  }

  private Transfer plainTransfer() {
    return service.create(
        DATE, checking.getId(), savings.getId(), BigDecimal.TEN, "Transfer", null);
  }

  private Transfer editTo(
      Transfer transfer, UUID from, UUID to, UUID productId, InvestmentTradeDetails details) {
    return service.edit(
        transfer.getId(), DATE, from, to, BigDecimal.TEN, "Edited", null, productId, details);
  }

  // --- create ---------------------------------------------------------------------------------

  @Test
  void aBuyIsATransferIntoAnInvestmentAccountTaggedWithItsProduct() {
    InvestmentTradeDetails details =
        new InvestmentTradeDetails(
            new BigDecimal("10"), new BigDecimal("100.00"), new BigDecimal("5.00"));

    Transfer created = buy(product.getId(), details, null);

    assertThat(created.getInvestmentProductId()).isEqualTo(product.getId());
    assertThat(created.getTradeDetails()).isEqualTo(details);
    assertThat(transferRepository.findById(created.getId())).isPresent();
  }

  @Test
  void aSellIsATransferOutOfAnInvestmentAccountTaggedWithItsProduct() {
    Transfer created =
        service.create(
            DATE,
            broker.getId(),
            checking.getId(),
            new BigDecimal("995.00"),
            "Sell",
            null,
            product.getId(),
            null,
            null);

    assertThat(created.getInvestmentProductId()).isEqualTo(product.getId());
    assertThat(created.getTradeDetails().isEmpty()).isTrue();
  }

  @Test
  void createRejectsAnInvestmentEndpointWithoutAProduct() {
    assertThatThrownBy(
            () ->
                service.create(
                    DATE,
                    checking.getId(),
                    broker.getId(),
                    BigDecimal.TEN,
                    "Buy",
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void createRejectsAProductWithNoHoldingInThatAccount() {
    assertThatThrownBy(() -> buy(otherBrokerProduct.getId(), null, null))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
    assertThat(transferRepository.findAll()).isEmpty();
  }

  @Test
  void createRejectsAProductWithoutAnInvestmentEndpoint() {
    assertThatThrownBy(
            () ->
                service.create(
                    DATE,
                    checking.getId(),
                    savings.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null,
                    product.getId(),
                    null,
                    null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void createRejectsTwoInvestmentEndpoints() {
    assertThatThrownBy(
            () ->
                service.create(
                    DATE,
                    broker.getId(),
                    otherBroker.getId(),
                    BigDecimal.TEN,
                    "Move",
                    null,
                    product.getId(),
                    null,
                    null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    DATE,
                    broker.getId(),
                    otherBroker.getId(),
                    BigDecimal.TEN,
                    "Move",
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void createRejectsAClosedHolding() {
    holding.close(LocalDate.of(2026, 3, 1));
    holdingRepository.save(holding);

    assertThatThrownBy(() -> buy(product.getId(), null, null))
        .isInstanceOf(InvestmentHoldingClosedException.class);
  }

  @Test
  void createRejectsAnUnknownProduct() {
    assertThatThrownBy(() -> buy(UUID.randomUUID(), null, null))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void createRejectsAClosedInvestmentAccount() {
    broker.close(LocalDate.of(2026, 3, 1));
    accountRepository.save(broker);

    assertThatThrownBy(() -> buy(product.getId(), null, null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void aPlainTransferStillNeedsNoProduct() {
    Transfer created = plainTransfer();

    assertThat(created.getInvestmentProductId()).isNull();
  }

  // --- edit -----------------------------------------------------------------------------------

  @Test
  void editCanTagAPlainTransferAsABuyAndChangeItsDetails() {
    Transfer created = plainTransfer();
    InvestmentTradeDetails details =
        new InvestmentTradeDetails(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE);

    Transfer edited = editTo(created, checking.getId(), broker.getId(), product.getId(), details);

    assertThat(edited.getInvestmentProductId()).isEqualTo(product.getId());
    assertThat(edited.getTradeDetails()).isEqualTo(details);
  }

  @Test
  void editRejectsAnInvestmentEndpointWithoutAProduct() {
    Transfer created = plainTransfer();

    assertThatThrownBy(() -> editTo(created, checking.getId(), broker.getId(), null, null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void editRejectsAProductWithNoHoldingInThatAccount() {
    Transfer created = plainTransfer();

    assertThatThrownBy(
            () ->
                editTo(created, checking.getId(), broker.getId(), otherBrokerProduct.getId(), null))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void editRejectsAProductWithoutAnInvestmentEndpoint() {
    Transfer created = buy(product.getId(), null, null);

    assertThatThrownBy(
            () -> editTo(created, checking.getId(), savings.getId(), product.getId(), null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void editRejectsTwoInvestmentEndpoints() {
    Transfer created = buy(product.getId(), null, null);

    assertThatThrownBy(
            () -> editTo(created, broker.getId(), otherBroker.getId(), product.getId(), null))
        .isInstanceOf(InvestmentTransferInvalidException.class);
  }

  @Test
  void editRejectsAClosedHolding() {
    Transfer created = buy(product.getId(), null, null);
    holding.close(LocalDate.of(2026, 3, 20));
    holdingRepository.save(holding);

    assertThatThrownBy(
            () -> editTo(created, checking.getId(), broker.getId(), product.getId(), null))
        .isInstanceOf(InvestmentHoldingClosedException.class);
  }

  @Test
  void editCanUntagATransferByMovingItBetweenCashAccounts() {
    Transfer created = buy(product.getId(), null, null);

    Transfer edited = editTo(created, checking.getId(), savings.getId(), null, null);

    assertThat(edited.getInvestmentProductId()).isNull();
  }

  @Test
  void editNeverTouchesSnapshots() {
    Transfer created = buy(product.getId(), null, new BigDecimal("1000.00"));
    List<InvestmentSnapshot> before = snapshotRepository.findByHoldingId(holding.getId());

    editTo(created, checking.getId(), broker.getId(), product.getId(), null);

    assertThat(snapshotRepository.findByHoldingId(holding.getId()))
        .hasSameSizeAs(before)
        .first()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("1000.00"));
  }

  // --- resulting balance ----------------------------------------------------------------------

  @Test
  void resultingBalanceWritesTheTransferAndASnapshotDatedTheTransferDate() {
    Transfer created = buy(product.getId(), null, new BigDecimal("2500.00"));

    List<InvestmentSnapshot> snapshots = snapshotRepository.findByHoldingId(holding.getId());
    assertThat(transferRepository.findById(created.getId())).isPresent();
    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.get(0).getDate()).isEqualTo(DATE);
    assertThat(snapshots.get(0).getBalance()).isEqualByComparingTo("2500.00");
  }

  @Test
  void resultingBalanceReplacesASameDaySnapshot() {
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), DATE, new BigDecimal("10.00")));

    buy(product.getId(), null, new BigDecimal("2500.00"));

    List<InvestmentSnapshot> snapshots = snapshotRepository.findByHoldingId(holding.getId());
    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.get(0).getBalance()).isEqualByComparingTo("2500.00");
  }

  @Test
  void resultingBalanceOfZeroRecordsAFullSell() {
    service.create(
        DATE,
        broker.getId(),
        checking.getId(),
        new BigDecimal("2500.00"),
        "Sold entire position",
        null,
        product.getId(),
        null,
        BigDecimal.ZERO);

    List<InvestmentSnapshot> snapshots = snapshotRepository.findByHoldingId(holding.getId());
    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.get(0).getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void noResultingBalanceWritesNoSnapshot() {
    buy(product.getId(), null, null);

    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void aRejectedTradeWritesNoSnapshot() {
    assertThatThrownBy(() -> buy(otherBrokerProduct.getId(), null, new BigDecimal("1.00")))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);

    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void resultingBalanceWithoutAProductIsRejected() {
    assertThatThrownBy(
            () ->
                service.create(
                    DATE,
                    checking.getId(),
                    savings.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null,
                    null,
                    null,
                    BigDecimal.TEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(transferRepository.findAll()).isEmpty();
  }

  // --- multi-holding (F022) ---------------------------------------------------------------------

  @Test
  void theSameProductCanBeTradedIndependentlyAtTwoHoldings() {
    InvestmentHolding otherHolding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(otherBroker.getId())
                .build());

    Transfer atBroker = buy(product.getId(), null, new BigDecimal("1000.00"));
    Transfer atOtherBroker =
        service.create(
            DATE,
            checking.getId(),
            otherBroker.getId(),
            new BigDecimal("500.00"),
            "Buy elsewhere",
            null,
            product.getId(),
            null,
            new BigDecimal("500.00"));

    assertThat(snapshotRepository.findByHoldingId(holding.getId()))
        .singleElement()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("1000.00"));
    assertThat(snapshotRepository.findByHoldingId(otherHolding.getId()))
        .singleElement()
        .satisfies(s -> assertThat(s.getBalance()).isEqualByComparingTo("500.00"));
    assertThat(atBroker.getId()).isNotEqualTo(atOtherBroker.getId());
  }
}
