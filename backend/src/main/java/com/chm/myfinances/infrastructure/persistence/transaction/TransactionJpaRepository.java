package com.chm.myfinances.infrastructure.persistence.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link TransactionJpaEntity}. Not exposed outside this package.
 *
 * <p>Extends {@link JpaSpecificationExecutor} so the adapter can compose the optional filter
 * dimensions (date range, category, account, payment method) into one dynamic {@code Specification}
 * rather than writing a derived-query method per combination.
 */
interface TransactionJpaRepository
    extends JpaRepository<TransactionJpaEntity, UUID>,
        JpaSpecificationExecutor<TransactionJpaEntity> {

  List<TransactionJpaEntity> findByAccountIdAndDateLessThanEqual(UUID accountId, LocalDate date);

  @Query(
      "select distinct t.date from TransactionJpaEntity t "
          + "where t.date >= :from and t.date <= :to")
  List<LocalDate> findDistinctDatesBetween(
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  boolean existsByCategoryId(UUID categoryId);

  boolean existsByPaymentMethodId(UUID paymentMethodId);

  boolean existsByAccountId(UUID accountId);

  boolean existsByVehicleId(UUID vehicleId);

  boolean existsByInvestmentHoldingId(UUID investmentHoldingId);

  @Query(
      "select coalesce(sum(t.amount), 0) from TransactionJpaEntity t "
          + "where t.categoryId = :categoryId and t.date >= :from and t.date <= :to")
  BigDecimal sumAmountByCategoryAndDateRange(
      @Param("categoryId") UUID categoryId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  @Query(
      "select t.categoryId as categoryId, sum(t.amount) as total from TransactionJpaEntity t "
          + "where t.type = com.chm.myfinances.domain.category.CategoryType.EXPENSE "
          + "and t.date >= :from and t.date <= :to group by t.categoryId")
  List<CategoryTotalProjection> sumExpenseAmountByCategoryForDateRange(
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
