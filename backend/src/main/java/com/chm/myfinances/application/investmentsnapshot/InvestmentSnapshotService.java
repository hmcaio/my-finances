package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentSnapshot} (F009 spec): {@link #record} upserts on {@code
 * (productId, date)} - one snapshot per product per day - and {@link #findByProduct} lists a
 * product's history, most recent first. New ids come from the {@link IdGenerator} port (ADR 0005).
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

  private void requireProduct(UUID productId) {
    if (!productRepository.existsById(productId)) {
      throw new InvestmentProductNotFoundException(productId);
    }
  }
}
