package com.chm.myfinances.infrastructure.persistence.allocationplan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link AllocationPlanEntryJpaEntity}. Not exposed outside this
 * package. {@link AllocationPlanVersionRepositoryAdapter} replaces a version's entries wholesale on
 * every save (delete-then-insert) rather than diffing, since {@code AllocationPlanEntry} is a value
 * object with no id of its own to match against.
 *
 * <p>{@link #deleteByVersionId} is a bulk {@code @Modifying} query, not a derived {@code
 * deleteBy...} method: a derived delete loads then schedules entity-by-entity removal for the next
 * flush, and Hibernate's flush ordering runs pending inserts before pending deletes - so the fresh
 * rows this adapter inserts right after would hit the still-present old rows' {@code UNIQUE
 * (version_id, investment_product_id)} constraint. A bulk query executes immediately.
 */
interface AllocationPlanEntryJpaRepository
    extends JpaRepository<AllocationPlanEntryJpaEntity, UUID> {

  List<AllocationPlanEntryJpaEntity> findByVersionId(UUID versionId);

  @Modifying
  @Query("delete from AllocationPlanEntryJpaEntity e where e.versionId = :versionId")
  void deleteByVersionId(@Param("versionId") UUID versionId);
}
