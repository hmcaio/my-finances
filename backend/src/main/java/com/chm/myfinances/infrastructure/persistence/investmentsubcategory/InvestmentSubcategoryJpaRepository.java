package com.chm.myfinances.infrastructure.persistence.investmentsubcategory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentSubcategoryJpaEntity}. Not exposed outside this
 * package.
 */
interface InvestmentSubcategoryJpaRepository
    extends JpaRepository<InvestmentSubcategoryJpaEntity, UUID> {}
