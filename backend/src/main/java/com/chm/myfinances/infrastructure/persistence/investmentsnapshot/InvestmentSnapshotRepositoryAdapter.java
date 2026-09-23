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

  @Override
  public InvestmentSnapshot save(InvestmentSnapshot snapshot) {
    InvestmentSnapshotJpaEntity entity =
        jpaRepository
            .findById(snapshot.getId())
            .map(
                existing -> {
                  existing.setBalance(snapshot.getBalance());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentSnapshotJpaEntity(
                        snapshot.getId(),
                        snapshot.getProductId(),
                        snapshot.getDate(),
                        snapshot.getBalance()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentSnapshot> findByProductIdAndDate(UUID productId, LocalDate date) {
    return jpaRepository
        .findByProductIdAndDate(productId, date)
        .map(InvestmentSnapshotRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentSnapshot> findByProductId(UUID productId) {
    return jpaRepository.findByProductIdOrderByDateDesc(productId).stream()
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
  public boolean existsByProductId(UUID productId) {
    return jpaRepository.existsByProductId(productId);
  }

  private static InvestmentSnapshot toDomain(InvestmentSnapshotJpaEntity entity) {
    return InvestmentSnapshot.reconstitute(
        entity.getId(), entity.getProductId(), entity.getDate(), entity.getBalance());
  }
}
