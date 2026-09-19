package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link PendingRecurringOccurrenceJpaEntity}. Not exposed outside this
 * package.
 */
interface PendingRecurringOccurrenceJpaRepository
    extends JpaRepository<PendingRecurringOccurrenceJpaEntity, UUID> {

  void deleteByTemplateId(UUID templateId);

  boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate);

  List<PendingRecurringOccurrenceJpaEntity> findByTemplateId(UUID templateId);

  /**
   * Atomic "insert unless this cycle already has a row". A plain {@code save} can't be used for
   * this: a second concurrent insert would fail the unique constraint at commit time, and inside a
   * caller-provided transaction that failure would also poison it. {@code ON CONFLICT DO NOTHING}
   * makes a lost race a normal return value. Being native SQL, it bypasses JPA auditing, so it
   * fills {@code created_at}/{@code last_modified_at} itself.
   *
   * @return rows inserted: {@code 1}, or {@code 0} if the cycle already had a row
   */
  @Modifying
  @Transactional
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO pending_recurring_occurrences
              (id, template_id, template_version_id, due_date, created_at, last_modified_at)
          VALUES (:id, :templateId, :templateVersionId, :dueDate, now(), now())
          ON CONFLICT (template_id, due_date) DO NOTHING
          """)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("templateId") UUID templateId,
      @Param("templateVersionId") UUID templateVersionId,
      @Param("dueDate") LocalDate dueDate);
}
