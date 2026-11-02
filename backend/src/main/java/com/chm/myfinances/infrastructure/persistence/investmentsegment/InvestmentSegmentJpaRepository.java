package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestmentSegmentJpaEntity}. Not exposed outside this package.
 */
interface InvestmentSegmentJpaRepository extends JpaRepository<InvestmentSegmentJpaEntity, UUID> {}
