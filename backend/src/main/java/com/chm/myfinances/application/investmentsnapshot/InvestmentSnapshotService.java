package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentSnapshot} (F009 spec): {@link #record} upserts on {@code
 * (productId, date)} - one snapshot per product per day - and {@link #findByProduct} lists a
 * product's history, most recent first. {@link #update} and {@link #delete} fix a wrongly dated or
 * mistaken entry (issue #59). New ids come from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>{@code record} is a single write, so it isn't {@code @Transactional} itself; {@code
 * TransferService.create} calls it from inside its own transaction when a trade carries a resulting
 * balance, and joins that one.
 */
@Service
public class InvestmentSnapshotService {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final InvestmentProductRepository productRepository;
  private final IdGenerator idGenerator;

  public InvestmentSnapshotService(
      InvestmentSnapshotRepository snapshotRepository,
      InvestmentProductRepository productRepository,
      IdGenerator idGenerator) {
    this.snapshotRepository = snapshotRepository;
    this.productRepository = productRepository;
    this.idGenerator = idGenerator;
  }

  /**
   * Records the product's value on {@code date}, replacing the balance of an existing same-day
   * snapshot. The product must exist (404); a closed product may still get a snapshot (a {@code 0}
   * correction, say).
   */
  public RecordedSnapshot record(UUID productId, LocalDate date, BigDecimal balance) {
    requireProduct(productId);
    return snapshotRepository
        .findByProductIdAndDate(productId, date)
        .map(
            existing -> {
              existing.replaceBalance(balance);
              return new RecordedSnapshot(snapshotRepository.save(existing), false);
            })
        .orElseGet(
            () ->
                new RecordedSnapshot(
                    snapshotRepository.save(
                        InvestmentSnapshot.create(idGenerator.newId(), productId, date, balance)),
                    true));
  }

  /** The product's snapshots, most recent date first. 404 for an unknown product. */
  public List<InvestmentSnapshot> findByProduct(UUID productId) {
    requireProduct(productId);
    return snapshotRepository.findByProductId(productId);
  }

  /**
   * Edits a snapshot's date and balance. 404 for an unknown product or a snapshot that isn't that
   * product's; 409 when the new date already holds another snapshot of the product, or when the
   * product is closed and the edit would leave its latest snapshot non-zero (PRD S5.8). One write,
   * so not {@code @Transactional}.
   */
  public InvestmentSnapshot update(
      UUID productId, UUID snapshotId, LocalDate date, BigDecimal balance) {
    InvestmentProduct product = requireProduct(productId);
    InvestmentSnapshot snapshot = requireSnapshotOf(productId, snapshotId);
    snapshotRepository
        .findByProductIdAndDate(productId, date)
        .filter(other -> !other.getId().equals(snapshotId))
        .ifPresent(
            other -> {
              throw new InvestmentSnapshotDateTakenException(productId);
            });
    if (product.isClosed()) {
      requireLatestIsZeroOrAbsent(
          productId,
          Stream.concat(
              otherSnapshots(productId, snapshotId), Stream.of(new DatedBalance(date, balance))));
    }
    snapshot.moveTo(date);
    snapshot.replaceBalance(balance);
    return snapshotRepository.save(snapshot);
  }

  /**
   * Deletes a snapshot. 404 as for {@link #update}; 409 when the product is closed and the
   * remaining latest snapshot would be non-zero. A trade's resulting-balance snapshot has no link
   * to its transfer, so the trade is untouched.
   */
  public void delete(UUID productId, UUID snapshotId) {
    InvestmentProduct product = requireProduct(productId);
    requireSnapshotOf(productId, snapshotId);
    if (product.isClosed()) {
      requireLatestIsZeroOrAbsent(productId, otherSnapshots(productId, snapshotId));
    }
    snapshotRepository.deleteById(snapshotId);
  }

  private record DatedBalance(LocalDate date, BigDecimal balance) {}

  private Stream<DatedBalance> otherSnapshots(UUID productId, UUID excludedSnapshotId) {
    return snapshotRepository.findByProductId(productId).stream()
        .filter(s -> !s.getId().equals(excludedSnapshotId))
        .map(s -> new DatedBalance(s.getDate(), s.getBalance()));
  }

  private void requireLatestIsZeroOrAbsent(UUID productId, Stream<DatedBalance> resulting) {
    boolean nonZeroLatest =
        resulting
            .max(Comparator.comparing(DatedBalance::date))
            .filter(latest -> latest.balance().signum() != 0)
            .isPresent();
    if (nonZeroLatest) {
      throw new InvestmentSnapshotClosedProductException(productId);
    }
  }

  private InvestmentSnapshot requireSnapshotOf(UUID productId, UUID snapshotId) {
    return snapshotRepository
        .findById(snapshotId)
        .filter(s -> s.getProductId().equals(productId))
        .orElseThrow(() -> new InvestmentSnapshotNotFoundException(snapshotId));
  }

  private InvestmentProduct requireProduct(UUID productId) {
    return productRepository
        .findById(productId)
        .orElseThrow(() -> new InvestmentProductNotFoundException(productId));
  }
}
