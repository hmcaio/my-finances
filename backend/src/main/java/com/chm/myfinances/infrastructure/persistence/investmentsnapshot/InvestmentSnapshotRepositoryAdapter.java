package com.chm.myfinances.infrastructure.persistence.investmentsnapshot;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentSnapshotRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link InvestmentSnapshot}
 * aggregate and {@link InvestmentSnapshotJpaEntity}.
 */
@Component
public class InvestmentSnapshotRepositoryAdapter implements InvestmentSnapshotRepository {

  private final InvestmentSnapshotJpaRepository jpaRepository;

  public InvestmentSnapshotRepositoryAdapter(InvestmentSnapshotJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  /**
   * {@code saveAndFlush}, not {@code save} (F025, issue #94 regression): {@code
   * InvestmentSnapshotService.insertSnapshot} relies on the {@code
   * uq_investment_snapshots_holding_date} constraint violation being thrown synchronously, inside
   * its own try/catch, so it can translate it to a 409. That only happens if this call actually
   * hits the database - once {@code record()} became {@code @Transactional} (F025, so the audit
   * insert shares its transaction), this save joined that outer transaction instead of committing
   * (and therefore flushing) on its own, so a plain {@code save()} silently deferred the insert -
   * and the constraint check - past the try/catch, to the outer commit, surfacing as an unmapped
   * exception instead of the 409.
   */
  @Override
  public InvestmentSnapshot save(InvestmentSnapshot snapshot) {
    InvestmentSnapshotJpaEntity entity =
        jpaRepository
            .findById(snapshot.getId())
            .map(
                existing -> {
                  existing.setDate(snapshot.getDate());
                  existing.setBalance(snapshot.getBalance());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentSnapshotJpaEntity(
                        snapshot.getId(),
                        snapshot.getHoldingId(),
                        snapshot.getDate(),
                        snapshot.getBalance()));
    return toDomain(jpaRepository.saveAndFlush(entity));
  }

  @Override
  public Optional<InvestmentSnapshot> findById(UUID id) {
    return jpaRepository.findById(id).map(InvestmentSnapshotRepositoryAdapter::toDomain);
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public Optional<InvestmentSnapshot> findByHoldingIdAndDate(UUID holdingId, LocalDate date) {
    return jpaRepository
        .findByHoldingIdAndDate(holdingId, date)
        .map(InvestmentSnapshotRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentSnapshot> findByHoldingId(UUID holdingId) {
    return jpaRepository.findByHoldingIdOrderByDateDesc(holdingId).stream()
        .map(InvestmentSnapshotRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<InvestmentSnapshot> findAllOnOrBefore(LocalDate asOfDate) {
    return jpaRepository.findByDateLessThanEqual(asOfDate).stream()
        .map(InvestmentSnapshotRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<InvestmentSnapshot> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentSnapshotRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByHoldingId(UUID holdingId) {
    return jpaRepository.existsByHoldingId(holdingId);
  }

  private static InvestmentSnapshot toDomain(InvestmentSnapshotJpaEntity entity) {
    return InvestmentSnapshot.reconstitute(
        entity.getId(), entity.getHoldingId(), entity.getDate(), entity.getBalance());
  }
}
