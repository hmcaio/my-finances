package com.chm.myfinances.infrastructure.persistence.investmentcategory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentCategoryJpaEntity}. Not exposed outside this package.
 */
interface InvestmentCategoryJpaRepository extends JpaRepository<InvestmentCategoryJpaEntity, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);
}
