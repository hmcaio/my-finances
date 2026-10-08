package com.chm.myfinances.infrastructure.persistence.transfer;

import java.util.List;
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
 * deleteBy...} method: backend {@code CLAUDE.md}'s "replacing a value-object child table wholesale
 * must delete via a bulk query" rule - a derived delete only schedules entity-by-entity removal for
 * the next flush, and Hibernate flushes pending inserts before pending deletes, so the fresh rows
 * this adapter inserts right after would hit the still-present old rows' unique constraints.
 */
interface TransferTradeLineJpaRepository extends JpaRepository<TransferTradeLineJpaEntity, UUID> {

  List<TransferTradeLineJpaEntity> findByTransferId(UUID transferId);

  List<TransferTradeLineJpaEntity> findByProductId(UUID productId);

  @Modifying
  @Query("delete from TransferTradeLineJpaEntity l where l.transferId = :transferId")
  void deleteByTransferId(@Param("transferId") UUID transferId);
}
