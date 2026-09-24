package com.chm.myfinances.application.transfer;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Transfer}: create/edit/delete/findById/findAll (F005 spec). New ids come
 * from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Coordinates against F003's {@link AccountRepository} to validate both accounts exist and are
 * open, and that they're not the same account - ordinary application-layer orchestration (ADR
 * 0004), not a domain-layer dependency: {@code domain/transfer} itself never imports {@code
 * domain.account}.
 *
 * <p>F009 makes a transfer able to be a buy/sell (ADR 0012). On create and edit, {@link
 * #requireValidInvestmentShape}: an endpoint that is an {@code INVESTMENT} account requires {@code
 * investmentProductId} and the product must belong to that account; a product requires exactly one
 * {@code INVESTMENT} endpoint (two isn't modeled); the product must be open. Direction is derived
 * (destination {@code INVESTMENT} = buy, source = sell), never stored. {@code create} also takes an
 * optional {@code resultingBalance}: when present it writes the transfer and a snapshot dated the
 * transfer date in one transaction (the only multi-write use case here, hence the method-level
 * {@code @Transactional}); editing never touches snapshots.
 */
@Service
public class TransferService {

  private static final Logger log = LoggerFactory.getLogger(TransferService.class);

  private final TransferRepository transferRepository;
  private final AccountRepository accountRepository;
  private final InvestmentProductRepository productRepository;
  private final InvestmentSnapshotService snapshotService;
  private final IdGenerator idGenerator;

  public TransferService(
      TransferRepository transferRepository,
      AccountRepository accountRepository,
      InvestmentProductRepository productRepository,
      InvestmentSnapshotService snapshotService,
      IdGenerator idGenerator) {
    this.transferRepository = transferRepository;
    this.accountRepository = accountRepository;
    this.productRepository = productRepository;
    this.snapshotService = snapshotService;
    this.idGenerator = idGenerator;
  }

  /** Creates a plain (non-investment) transfer. */
  public Transfer create(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    return create(
        date, fromAccountId, toAccountId, amount, description, additionalNotes, null, null, null);
  }

  /**
   * Creates a transfer, a buy/sell when tagged with {@code investmentProductId}. A non-null {@code
   * resultingBalance} (requires a product) also records a snapshot of that product dated {@code
   * date}, replacing a same-day one - atomically with the transfer. Every validation runs before
   * the first write.
   */
  @Transactional
  public Transfer create(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      UUID investmentProductId,
      InvestmentTradeDetails tradeDetails,
      BigDecimal resultingBalance) {
    if (resultingBalance != null && investmentProductId == null) {
      throw new IllegalArgumentException("resultingBalance requires an investmentProductId");
    }
    requireDifferentAccounts(fromAccountId, toAccountId);
    Account fromAccount = requireOpenAccount(fromAccountId);
    Account toAccount = requireOpenAccount(toAccountId);
    requireValidInvestmentShape(fromAccount, toAccount, investmentProductId);

    Transfer transfer =
        Transfer.create(
            idGenerator.newId(),
            date,
            fromAccount.getId(),
            toAccount.getId(),
            amount,
            description,
            additionalNotes,
            investmentProductId,
            tradeDetails);
    Transfer saved = transferRepository.save(transfer);
    if (resultingBalance != null) {
      snapshotService.record(investmentProductId, date, resultingBalance);
      log.info(
          "Recorded snapshot for investment product {} as part of transfer {}",
          investmentProductId,
          saved.getId());
    }
    return saved;
  }

  public Transfer findById(UUID id) {
    return transferRepository.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
  }

  public Page<Transfer> findAll(TransferFilter filter, Pageable pageable) {
    return transferRepository.findAll(filter, pageable);
  }

  /** Edits a plain (non-investment) transfer, clearing any investment tag it had. */
  public Transfer edit(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    return edit(
        id, date, fromAccountId, toAccountId, amount, description, additionalNotes, null, null);
  }

  /** Full-replace edit including the investment tag and trade details; snapshots are untouched. */
  public Transfer edit(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes,
      UUID investmentProductId,
      InvestmentTradeDetails tradeDetails) {
    Transfer transfer = findById(id);
    requireDifferentAccounts(fromAccountId, toAccountId);
    Account fromAccount = requireOpenAccount(fromAccountId);
    Account toAccount = requireOpenAccount(toAccountId);
    requireValidInvestmentShape(fromAccount, toAccount, investmentProductId);

    transfer.edit(
        date,
        fromAccount.getId(),
        toAccount.getId(),
        amount,
        description,
        additionalNotes,
        investmentProductId,
        tradeDetails);
    return transferRepository.save(transfer);
  }

  public void delete(UUID id) {
    if (!transferRepository.existsById(id)) {
      throw new TransferNotFoundException(id);
    }
    transferRepository.deleteById(id);
  }

  /**
   * Rejects a transfer to/from the same account (F005 spec's invariant) before either account is
   * even looked up - checked at the application layer, mapped to a proper 400 here, with {@link
   * Transfer}'s own constructor/{@code edit} enforcing the same rule again at the domain level as
   * defense in depth.
   */
  private void requireDifferentAccounts(UUID fromAccountId, UUID toAccountId) {
    if (fromAccountId.equals(toAccountId)) {
      throw new SameAccountTransferException(fromAccountId);
    }
  }

  /**
   * Resolves an account and rejects a closed one (F005 spec: "Both accounts must be open ... at
   * creation time"). Applied on both create and edit - edit can move a transfer onto a different,
   * possibly-closed account just as easily as create can target one directly (same convention as
   * F004's {@code TransactionService.requireOpenAccount}).
   */
  private Account requireOpenAccount(UUID accountId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (account.isClosed()) {
      throw new AccountClosedException(accountId);
    }
    return account;
  }

  /**
   * The F009 investment rules (see the class javadoc), all 409 because they depend on persisted
   * account/product state. An unknown product is a 404.
   */
  private void requireValidInvestmentShape(
      Account fromAccount, Account toAccount, UUID investmentProductId) {
    boolean fromInvestment = fromAccount.getType() == AccountType.INVESTMENT;
    boolean toInvestment = toAccount.getType() == AccountType.INVESTMENT;
    if (fromInvestment && toInvestment) {
      throw new InvestmentTransferInvalidException(
          "a transfer between two investment accounts is not supported");
    }
    boolean hasInvestmentEndpoint = fromInvestment || toInvestment;
    if (investmentProductId == null) {
      if (hasInvestmentEndpoint) {
        throw new InvestmentTransferInvalidException(
            "a transfer with an investment account requires an investment product");
      }
      return;
    }
    InvestmentProduct product =
        productRepository
            .findById(investmentProductId)
            .orElseThrow(() -> new InvestmentProductNotFoundException(investmentProductId));
    if (!hasInvestmentEndpoint) {
      throw new InvestmentTransferInvalidException(
          "an investment product requires an investment account on one side");
    }
    Account investmentAccount = fromInvestment ? fromAccount : toAccount;
    if (!product.getAccountId().equals(investmentAccount.getId())) {
      throw new InvestmentTransferInvalidException(
          "the investment product does not belong to the investment account");
    }
    if (product.isClosed()) {
      throw new InvestmentProductClosedException(investmentProductId);
    }
  }
}
