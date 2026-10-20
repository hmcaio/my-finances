package com.chm.myfinances.infrastructure.persistence.institution;

import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InstitutionRepository} port on top of Spring Data/
 * Hibernate (ADR 0004). Translates between the framework-free {@link Institution} aggregate and
 * {@link InstitutionJpaEntity}.
 */
@Component
public class InstitutionRepositoryAdapter implements InstitutionRepository {

  private final InstitutionJpaRepository jpaRepository;

  public InstitutionRepositoryAdapter(InstitutionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Institution save(Institution institution) {
    InstitutionJpaEntity entity =
        jpaRepository
            .findById(institution.getId())
            .map(
                existing -> {
                  existing.setName(institution.getName());
                  return existing;
                })
            .orElseGet(() -> new InstitutionJpaEntity(institution.getId(), institution.getName()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Institution> findById(UUID id) {
    return jpaRepository.findById(id).map(InstitutionRepositoryAdapter::toDomain);
  }

  @Override
  public List<Institution> findAll() {
    return jpaRepository.findAll().stream().map(InstitutionRepositoryAdapter::toDomain).toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return jpaRepository.existsById(id);
  }

  @Override
  public boolean existsByName(String name) {
    return jpaRepository.existsByName(name);
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return jpaRepository.existsByNameAndIdNot(name, excludedId);
  }

  private static Institution toDomain(InstitutionJpaEntity entity) {
    return Institution.reconstitute(entity.getId(), entity.getName(), entity.isBuiltIn());
  }
}
