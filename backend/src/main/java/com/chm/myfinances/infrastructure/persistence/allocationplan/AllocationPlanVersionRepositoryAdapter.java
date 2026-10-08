package com.chm.myfinances.infrastructure.persistence.allocationplan;

import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing the domain's {@link AllocationPlanVersionRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link AllocationPlanVersion}
 * aggregate (with its {@link AllocationPlanEntry} value objects) and two tables: {@code
 * allocation_plan_versions} and {@code allocation_plan_entries}.
 *
 * <p>{@link AllocationPlanEntry} is a value object with no id of its own, so {@link #save} always
 * replaces a version's entries wholesale - delete every existing row for the version, then insert a
 * fresh one per entry, each with a new id from {@link IdGenerator} (ADR 0005: a persistence-only
 * row still counts as "a new entity" needing an id from the single generator port, even though the
 * domain-level {@code AllocationPlanEntry} carries none). Two writes, so {@code @Transactional}.
 */
@Component
public class AllocationPlanVersionRepositoryAdapter implements AllocationPlanVersionRepository {

  private final AllocationPlanVersionJpaRepository versionJpaRepository;
  private final AllocationPlanEntryJpaRepository entryJpaRepository;
  private final IdGenerator idGenerator;

  public AllocationPlanVersionRepositoryAdapter(
      AllocationPlanVersionJpaRepository versionJpaRepository,
      AllocationPlanEntryJpaRepository entryJpaRepository,
      IdGenerator idGenerator) {
    this.versionJpaRepository = versionJpaRepository;
    this.entryJpaRepository = entryJpaRepository;
    this.idGenerator = idGenerator;
  }

  @Override
  @Transactional
  public AllocationPlanVersion save(AllocationPlanVersion version) {
    AllocationPlanVersionJpaEntity entity =
        versionJpaRepository
            .findById(version.getId())
            .map(
                existing -> {
                  existing.setPlanId(version.getPlanId());
                  existing.setEffectiveFrom(toFirstOfMonth(version.getEffectiveFrom()));
                  return existing;
                })
            .orElseGet(
                () ->
                    new AllocationPlanVersionJpaEntity(
                        version.getId(),
                        version.getPlanId(),
                        toFirstOfMonth(version.getEffectiveFrom())));
    AllocationPlanVersionJpaEntity saved = versionJpaRepository.save(entity);

    entryJpaRepository.deleteByVersionId(version.getId());
    for (AllocationPlanEntry entry : version.getEntries()) {
      entryJpaRepository.save(
          new AllocationPlanEntryJpaEntity(
              idGenerator.newId(),
              version.getId(),
              entry.investmentProductId(),
              entry.targetPercentage()));
    }

    return toDomain(saved, entryJpaRepository.findByVersionId(version.getId()));
  }

  @Override
  public List<AllocationPlanVersion> findByPlanId(UUID planId) {
    return versionJpaRepository.findByPlanId(planId).stream()
        .map(entity -> toDomain(entity, entryJpaRepository.findByVersionId(entity.getId())))
        .toList();
  }

  @Override
  public Optional<AllocationPlanVersion> findByPlanIdAndEffectiveFrom(
      UUID planId, YearMonth effectiveFrom) {
    return versionJpaRepository
        .findByPlanIdAndEffectiveFrom(planId, toFirstOfMonth(effectiveFrom))
        .map(entity -> toDomain(entity, entryJpaRepository.findByVersionId(entity.getId())));
  }

  private static LocalDate toFirstOfMonth(YearMonth yearMonth) {
    return yearMonth.atDay(1);
  }

  private static AllocationPlanVersion toDomain(
      AllocationPlanVersionJpaEntity entity, List<AllocationPlanEntryJpaEntity> entryEntities) {
    List<AllocationPlanEntry> entries =
        entryEntities.stream()
            .map(e -> new AllocationPlanEntry(e.getInvestmentProductId(), e.getTargetPercentage()))
            .toList();
    return AllocationPlanVersion.reconstitute(
        entity.getId(), entity.getPlanId(), entries, YearMonth.from(entity.getEffectiveFrom()));
  }
}
