package com.chm.myfinances.application.investmentopeningposition;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingClosedException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link InvestmentOpeningPosition} (F038 spec, ADR 0033): a holding's starting
 * quantity and average unit price, recorded without moving cash. Keyed by the holding - at most one
 * exists per holding - so {@link #upsert} creates or replaces and there is no position id in any
 * URL. New ids come from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>{@link #upsert} verifies, in order: the holding exists (404) and is open (409, {@link
 * InvestmentHoldingClosedException}); the values are valid (400, {@link
 * InvalidOpeningPositionException} - a future as-of date against the injected {@link Clock}, which
 * the domain does not have, or the domain's own {@link IllegalArgumentException}); the as-of date
 * is not after the holding's earliest trade (409, {@link OpeningPositionAfterTradesException}).
 * When a current value is given the holding's snapshot dated the as-of date is recorded in the same
 * transaction through {@link InvestmentSnapshotService#record} (replacing a same-day one, exactly
 * as a trade's resulting balance does); without one no snapshot is written or touched, because the
 * snapshot, not the position, is the sole source of value (ADR 0012).
 *
 * <p>Both write methods are {@code @Transactional}: the position, the optional snapshot and the
 * audit entry are separate writes. When called from {@code InvestmentProductService.create} they
 * join its transaction, so a failure rolls back the product and holding as well.
 */
@Service
public class InvestmentOpeningPositionService {

  private final InvestmentOpeningPositionRepository positionRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final TransferTradeLineRepository tradeLineRepository;
  private final InvestmentSnapshotService snapshotService;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final AuditRecorder auditRecorder;

  public InvestmentOpeningPositionService(
      InvestmentOpeningPositionRepository positionRepository,
      InvestmentHoldingRepository holdingRepository,
      TransferTradeLineRepository tradeLineRepository,
      InvestmentSnapshotService snapshotService,
      IdGenerator idGenerator,
      Clock clock,
      AuditRecorder auditRecorder) {
    this.positionRepository = positionRepository;
    this.holdingRepository = holdingRepository;
    this.tradeLineRepository = tradeLineRepository;
    this.snapshotService = snapshotService;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.auditRecorder = auditRecorder;
  }

  /** Creates the holding's opening position, or replaces the one it has. */
  @Transactional
  public RecordedOpeningPosition upsert(
      UUID holdingId,
      LocalDate asOfDate,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal currentValue) {
    InvestmentHolding holding = requireOpenHolding(holdingId);
    requireNotInTheFuture(asOfDate);
    if (currentValue != null && currentValue.signum() < 0) {
      throw new InvalidOpeningPositionException("currentValue must not be negative");
    }
    Optional<InvestmentOpeningPosition> existing = positionRepository.findByHoldingId(holdingId);
    InvestmentOpeningPosition saved =
        existing.isPresent()
            ? replace(existing.get(), asOfDate, quantity, unitPrice, holding)
            : insert(holdingId, asOfDate, quantity, unitPrice, holding);
    if (currentValue != null) {
      snapshotService.record(holdingId, asOfDate, currentValue);
    }
    return new RecordedOpeningPosition(saved, existing.isEmpty());
  }

  private InvestmentOpeningPosition insert(
      UUID holdingId,
      LocalDate asOfDate,
      BigDecimal quantity,
      BigDecimal unitPrice,
      InvestmentHolding holding) {
    InvestmentOpeningPosition position;
    try {
      position =
          InvestmentOpeningPosition.create(
              idGenerator.newId(), holdingId, asOfDate, quantity, unitPrice);
    } catch (IllegalArgumentException e) {
      throw new InvalidOpeningPositionException(e.getMessage());
    }
    requireNotAfterEarliestTrade(holding, asOfDate);
    InvestmentOpeningPosition saved = positionRepository.save(position);
    auditRecorder.recordCreate(
        AuditEntityType.INVESTMENT_OPENING_POSITION, saved.getId(), null, saved.toAuditSnapshot());
    return saved;
  }

  private InvestmentOpeningPosition replace(
      InvestmentOpeningPosition position,
      LocalDate asOfDate,
      BigDecimal quantity,
      BigDecimal unitPrice,
      InvestmentHolding holding) {
    Map<String, Object> before = position.toAuditSnapshot();
    try {
      position.edit(asOfDate, quantity, unitPrice);
    } catch (IllegalArgumentException e) {
      throw new InvalidOpeningPositionException(e.getMessage());
    }
    requireNotAfterEarliestTrade(holding, asOfDate);
    InvestmentOpeningPosition saved = positionRepository.save(position);
    auditRecorder.recordUpdate(
        AuditEntityType.INVESTMENT_OPENING_POSITION,
        saved.getId(),
        null,
        before,
        saved.toAuditSnapshot());
    return saved;
  }

  /** Removes the holding's opening position. Snapshots are left alone, as for a deleted trade. */
  @Transactional
  public void delete(UUID holdingId) {
    InvestmentHolding holding = requireHolding(holdingId);
    InvestmentOpeningPosition position =
        positionRepository
            .findByHoldingId(holdingId)
            .orElseThrow(() -> new InvestmentOpeningPositionNotFoundException(holdingId));
    requireOpen(holding);
    positionRepository.deleteById(position.getId());
    auditRecorder.recordDelete(
        AuditEntityType.INVESTMENT_OPENING_POSITION,
        position.getId(),
        null,
        position.toAuditSnapshot());
  }

  /** The holding's position. 404 for an unknown holding, and a distinct 404 for no position. */
  public InvestmentOpeningPosition findByHoldingId(UUID holdingId) {
    requireHolding(holdingId);
    return positionRepository
        .findByHoldingId(holdingId)
        .orElseThrow(() -> new InvestmentOpeningPositionNotFoundException(holdingId));
  }

  private void requireNotInTheFuture(LocalDate asOfDate) {
    if (asOfDate != null && asOfDate.isAfter(LocalDate.now(clock))) {
      throw new InvalidOpeningPositionException("asOfDate must not be in the future");
    }
  }

  /** The same-day trade is allowed; only a trade strictly before the position's date conflicts. */
  private void requireNotAfterEarliestTrade(InvestmentHolding holding, LocalDate asOfDate) {
    tradeLineRepository
        .findEarliestDateByHolding(holding.getProductId(), holding.getAccountId())
        .filter(earliest -> asOfDate.isAfter(earliest))
        .ifPresent(
            earliest -> {
              throw new OpeningPositionAfterTradesException(holding.getId());
            });
  }

  private InvestmentHolding requireOpenHolding(UUID holdingId) {
    InvestmentHolding holding = requireHolding(holdingId);
    requireOpen(holding);
    return holding;
  }

  private static void requireOpen(InvestmentHolding holding) {
    if (holding.isClosed()) {
      throw new InvestmentHoldingClosedException(holding.getId());
    }
  }

  private InvestmentHolding requireHolding(UUID holdingId) {
    return holdingRepository
        .findById(holdingId)
        .orElseThrow(() -> new InvestmentHoldingNotFoundException(holdingId));
  }
}
