package com.chm.myfinances.infrastructure.persistence.investmentsnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentSnapshotJpaEntity}. Not exposed outside this package.
 */
interface InvestmentSnapshotJpaRepository extends JpaRepository<InvestmentSnapshotJpaEntity, UUID> {

  Optional<InvestmentSnapshotJpaEntity> findByProductIdAndDate(UUID productId, LocalDate date);

  List<InvestmentSnapshotJpaEntity> findByProductIdOrderByDateDesc(UUID productId);

  List<InvestmentSnapshotJpaEntity> findByDateLessThanEqual(LocalDate date);

  boolean existsByProductId(UUID productId);
}
