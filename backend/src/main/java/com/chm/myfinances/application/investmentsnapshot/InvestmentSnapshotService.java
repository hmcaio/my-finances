package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentSnapshot} (F009 spec, rekeyed by holding for F022/ADR 0020):
 * {@link #record} upserts on {@code (holdingId, date)} - one snapshot per holding per day - and
 * {@link #findByHolding} lists a holding's history, most recent first. {@link #update} and {@link
 * #delete} fix a wrongly dated or mistaken entry (issue #59). New ids come from the {@link
 * IdGenerator} port (ADR 0005).
 *
 * <p>{@code record} is a single write, so it isn't {@code @Transactional} itself; {@code
 * TransferService.create} calls it from inside its own transaction when a trade carries a resulting
 * balance, and joins that one.
 *
 * <p>{@code record}'s find-then-insert on {@code (holdingId, date)} has a TOCTOU gap: two
 * concurrent calls for the same holding and date can both see "absent" and both attempt an insert,
 * tripping the DB's {@code uq_investment_snapshots_holding_date} constraint on the loser. That's
 * caught and translated to the same 409 {@link InvestmentSnapshotDateTakenException} {@link
 * #update} throws for the equivalent case, rather than a generic 500 (issue #94).
 */
@Service
public class InvestmentSnapshotService {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final IdGenerator idGenerator;

  public InvestmentSnapshotService(
      InvestmentSnapshotRepository snapshotRepository,
      InvestmentHoldingRepository holdingRepository,
      IdGenerator idGenerator) {
    this.snapshotRepository = snapshotRepository;
    this.holdingRepository = holdingRepository;
    this.idGenerator = idGenerator;
  }

  /**
   * Records the holding's value on {@code date}, replacing the balance of an existing same-day
   * snapshot. The holding must exist (404); a closed holding may still get a snapshot (a {@code 0}
   * correction, say).
   */
  public RecordedSnapshot record(UUID holdingId, LocalDate date, BigDecimal balance) {
    requireHolding(holdingId);
    return snapshotRepository
        .findByHoldingIdAndDate(holdingId, date)
        .map(
            existing -> {
              existing.replaceBalance(balance);
              return new RecordedSnapshot(snapshotRepository.save(existing), false);
            })
        .orElseGet(() -> insertSnapshot(holdingId, date, balance));
  }

  private RecordedSnapshot insertSnapshot(UUID holdingId, LocalDate date, BigDecimal balance) {
    try {
      return new RecordedSnapshot(
          snapshotRepository.save(
              InvestmentSnapshot.create(idGenerator.newId(), holdingId, date, balance)),
          true);
    } catch (DataIntegrityViolationException e) {
      throw new InvestmentSnapshotDateTakenException(holdingId);
    }
  }

  /** The holding's snapshots, most recent date first. 404 for an unknown holding. */
  public List<InvestmentSnapshot> findByHolding(UUID holdingId) {
    requireHolding(holdingId);
    return snapshotRepository.findByHoldingId(holdingId);
  }

  /**
   * Edits a snapshot's date and balance. 404 for an unknown holding or a snapshot that isn't that
   * holding's; 409 when the new date already holds another snapshot of the holding, or when the
   * holding is closed and the edit would leave its latest snapshot non-zero (PRD S5.8). One write,
   * so not {@code @Transactional}.
   */
  public InvestmentSnapshot update(
      UUID holdingId, UUID snapshotId, LocalDate date, BigDecimal balance) {
    InvestmentHolding holding = requireHolding(holdingId);
    InvestmentSnapshot snapshot = requireSnapshotOf(holdingId, snapshotId);
    snapshotRepository
        .findByHoldingIdAndDate(holdingId, date)
        .filter(other -> !other.getId().equals(snapshotId))
        .ifPresent(
            other -> {
              throw new InvestmentSnapshotDateTakenException(holdingId);
            });
    if (holding.isClosed()) {
      requireLatestIsZeroOrAbsent(
          holdingId,
          Stream.concat(
              otherSnapshots(holdingId, snapshotId), Stream.of(new DatedBalance(date, balance))));
    }
    snapshot.moveTo(date);
    snapshot.replaceBalance(balance);
    return snapshotRepository.save(snapshot);
  }

  /**
   * Deletes a snapshot. 404 as for {@link #update}; 409 when the holding is closed and the
   * remaining latest snapshot would be non-zero. A trade's resulting-balance snapshot has no link
   * to its transfer, so the trade is untouched.
   */
  public void delete(UUID holdingId, UUID snapshotId) {
    InvestmentHolding holding = requireHolding(holdingId);
    requireSnapshotOf(holdingId, snapshotId);
    if (holding.isClosed()) {
      requireLatestIsZeroOrAbsent(holdingId, otherSnapshots(holdingId, snapshotId));
    }
    snapshotRepository.deleteById(snapshotId);
  }

  private record DatedBalance(LocalDate date, BigDecimal balance) {}

  private Stream<DatedBalance> otherSnapshots(UUID holdingId, UUID excludedSnapshotId) {
    return snapshotRepository.findByHoldingId(holdingId).stream()
        .filter(s -> !s.getId().equals(excludedSnapshotId))
        .map(s -> new DatedBalance(s.getDate(), s.getBalance()));
  }

  private void requireLatestIsZeroOrAbsent(UUID holdingId, Stream<DatedBalance> resulting) {
    boolean nonZeroLatest =
        resulting
            .max(Comparator.comparing(DatedBalance::date))
            .filter(latest -> latest.balance().signum() != 0)
            .isPresent();
    if (nonZeroLatest) {
      throw new InvestmentSnapshotClosedHoldingException(holdingId);
    }
  }

  private InvestmentSnapshot requireSnapshotOf(UUID holdingId, UUID snapshotId) {
    return snapshotRepository
        .findById(snapshotId)
        .filter(s -> s.getHoldingId().equals(holdingId))
        .orElseThrow(() -> new InvestmentSnapshotNotFoundException(snapshotId));
  }

  private InvestmentHolding requireHolding(UUID holdingId) {
    return holdingRepository
        .findById(holdingId)
        .orElseThrow(() -> new InvestmentHoldingNotFoundException(holdingId));
  }
}
