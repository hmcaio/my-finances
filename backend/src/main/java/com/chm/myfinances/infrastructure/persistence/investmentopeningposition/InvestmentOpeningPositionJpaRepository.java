package com.chm.myfinances.infrastructure.persistence.investmentopeningposition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentOpeningPositionJpaEntity}. Not exposed outside this
 * package.
 */
interface InvestmentOpeningPositionJpaRepository
    extends JpaRepository<InvestmentOpeningPositionJpaEntity, UUID> {

  Optional<InvestmentOpeningPositionJpaEntity> findByHoldingId(UUID holdingId);

  List<InvestmentOpeningPositionJpaEntity> findByHoldingIdIn(Collection<UUID> holdingIds);

  boolean existsByHoldingId(UUID holdingId);
}
