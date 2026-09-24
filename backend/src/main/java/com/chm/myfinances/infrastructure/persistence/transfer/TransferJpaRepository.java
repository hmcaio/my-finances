package com.chm.myfinances.infrastructure.persistence.transfer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link TransferJpaEntity}. Not exposed outside this package.
 *
 * <p>Extends {@link JpaSpecificationExecutor} for the same reason {@code TransactionJpaRepository}
 * does - composing {@code TransferFilter}'s optional dimensions into one dynamic {@code
 * Specification}. {@link #findByAccountIdOnOrBefore} needs a hand-written JPQL query rather than a
 * derived method name, since it matches {@code accountId} against either of two columns (from/to) -
 * Spring Data's derived-query naming can't express that OR cleanly.
 */
interface TransferJpaRepository
    extends JpaRepository<TransferJpaEntity, UUID>, JpaSpecificationExecutor<TransferJpaEntity> {

  @Query(
      "select t from TransferJpaEntity t where t.date <= :asOfDate "
          + "and (t.fromAccountId = :accountId or t.toAccountId = :accountId)")
  List<TransferJpaEntity> findByAccountIdOnOrBefore(
      @Param("accountId") UUID accountId, @Param("asOfDate") LocalDate asOfDate);

  List<TransferJpaEntity> findByInvestmentProductId(UUID investmentProductId);

  List<TransferJpaEntity> findByInvestmentProductIdIsNotNull();

  boolean existsByInvestmentProductId(UUID investmentProductId);
}
