package com.chm.myfinances.infrastructure.persistence.category;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link CategoryRepository} port on top of Spring Data/Hibernate
 * (ADR 0004). Translates between the framework-free {@link Category} aggregate and {@link
 * CategoryJpaEntity}.
 */
@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

  private final CategoryJpaRepository jpaRepository;

  public CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Category save(Category category) {
    CategoryJpaEntity entity =
        jpaRepository
            .findById(category.getId())
            .map(
                existing -> {
                  existing.setName(category.getName());
                  return existing;
                })
            .orElseGet(
                () ->
                    new CategoryJpaEntity(
                        category.getId(), category.getName(), category.getType()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Category> findById(UUID id) {
    return jpaRepository.findById(id).map(CategoryRepositoryAdapter::toDomain);
  }

  @Override
  public List<Category> findAll() {
    return jpaRepository.findAll().stream().map(CategoryRepositoryAdapter::toDomain).toList();
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

  private static Category toDomain(CategoryJpaEntity entity) {
    return Category.reconstitute(
        entity.getId(),
        entity.getName(),
        entity.getType(),
        entity.isBuiltIn(),
        entity.isFuelCategory(),
        entity.isDividendCategory());
  }
}
