package com.chm.myfinances.infrastructure.persistence.transfer;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link TransferTradeLineJpaEntity}. Not exposed outside this package.
 * {@link TransferRepositoryAdapter} replaces a transfer's lines wholesale on every save
 * (delete-then-insert), since {@code TradeConfirmationLine} is a value object with no id of its own
 * to match against - same pattern as {@code AllocationPlanEntryJpaRepository}.
 *
 * <p>{@link #deleteByTransferId} is a bulk {@code @Modifying} query, not a derived {@code
 * deleteBy...} method: {@code docs/conventions/backend-structure.md}'s "replacing a value-object
 * child table wholesale must delete via a bulk query" rule - a derived delete only schedules
 * entity-by-entity removal for the next flush, and Hibernate flushes pending inserts before pending
 * deletes, so the fresh rows this adapter inserts right after would hit the still-present old rows'
 * unique constraints.
 */
interface TransferTradeLineJpaRepository extends JpaRepository<TransferTradeLineJpaEntity, UUID> {

  List<TransferTradeLineJpaEntity> findByTransferId(UUID transferId);

  /**
   * One statement for a whole page or account history, instead of one per transfer (issue #189).
   */
  List<TransferTradeLineJpaEntity> findByTransferIdIn(Collection<UUID> transferIds);

  List<TransferTradeLineJpaEntity> findByProductId(UUID productId);

  /**
   * The earliest date of a transfer carrying a line of this product with the account on either side
   * (F038): backs the opening position's date-order guard. Empty when there is none.
   */
  @Query(
      "select min(t.date) from TransferTradeLineJpaEntity l, TransferJpaEntity t"
          + " where t.id = l.transferId and l.productId = :productId"
          + " and (t.fromAccountId = :accountId or t.toAccountId = :accountId)")
  Optional<LocalDate> findEarliestDate(
      @Param("productId") UUID productId, @Param("accountId") UUID accountId);

  @Modifying
  @Query("delete from TransferTradeLineJpaEntity l where l.transferId = :transferId")
  void deleteByTransferId(@Param("transferId") UUID transferId);
}
