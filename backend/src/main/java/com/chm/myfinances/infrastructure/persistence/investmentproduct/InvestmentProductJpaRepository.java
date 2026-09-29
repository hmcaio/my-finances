package com.chm.myfinances.infrastructure.persistence.investmentproduct;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentProductJpaEntity}. Not exposed outside this package.
 */
interface InvestmentProductJpaRepository extends JpaRepository<InvestmentProductJpaEntity, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  boolean existsByInvestmentCategoryId(UUID investmentCategoryId);

  boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId);
}
