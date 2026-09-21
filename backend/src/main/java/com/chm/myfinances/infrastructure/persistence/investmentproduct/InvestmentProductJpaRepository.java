package com.chm.myfinances.infrastructure.persistence.investmentproduct;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentProductJpaEntity}. Not exposed outside this package.
 */
interface InvestmentProductJpaRepository extends JpaRepository<InvestmentProductJpaEntity, UUID> {

  List<InvestmentProductJpaEntity> findByAccountId(UUID accountId);

  boolean existsByAccountIdAndName(UUID accountId, String name);

  boolean existsByAccountIdAndNameAndIdNot(UUID accountId, String name, UUID id);

  boolean existsByAccountIdAndClosedDateIsNull(UUID accountId);

  boolean existsByInvestmentCategoryId(UUID investmentCategoryId);

  boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId);
}
