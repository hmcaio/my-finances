package com.chm.myfinances.infrastructure.persistence.investmentsubcategory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentSubcategoryJpaEntity}. Not exposed outside this
 * package.
 */
interface InvestmentSubcategoryJpaRepository
    extends JpaRepository<InvestmentSubcategoryJpaEntity, UUID> {

  boolean existsByInvestmentCategoryId(UUID investmentCategoryId);

  boolean existsByInvestmentCategoryIdAndName(UUID investmentCategoryId, String name);

  boolean existsByInvestmentCategoryIdAndNameAndIdNot(
      UUID investmentCategoryId, String name, UUID id);
}
