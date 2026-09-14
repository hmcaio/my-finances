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
}
