package com.chm.myfinances.infrastructure.persistence.investmentholding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentHoldingJpaEntity}. Not exposed outside this package.
 */
interface InvestmentHoldingJpaRepository extends JpaRepository<InvestmentHoldingJpaEntity, UUID> {

  List<InvestmentHoldingJpaEntity> findByProductId(UUID productId);

  List<InvestmentHoldingJpaEntity> findByAccountId(UUID accountId);

  Optional<InvestmentHoldingJpaEntity> findByProductIdAndAccountId(UUID productId, UUID accountId);

  boolean existsByProductIdAndAccountId(UUID productId, UUID accountId);

  boolean existsByProductId(UUID productId);

  boolean existsByAccountId(UUID accountId);

  boolean existsByAccountIdAndClosedDateIsNull(UUID accountId);
}
