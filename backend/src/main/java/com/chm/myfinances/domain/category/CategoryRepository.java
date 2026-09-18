package com.chm.myfinances.domain.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Category} (ADR 0004: domain/application logic sits behind ports,
 * isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/category}.
 */
public interface CategoryRepository {

  Category save(Category category);

  Optional<Category> findById(UUID id);

  List<Category> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether a Category already has this exact name - backs the create-time duplicate guard. */
  boolean existsByName(String name);

  /**
   * Whether a Category other than {@code excludedId} already has this exact name - backs the
   * rename-time duplicate guard without rejecting a no-op rename to the category's own current
   * name.
   */
  boolean existsByNameAndIdNot(String name, UUID excludedId);
}
