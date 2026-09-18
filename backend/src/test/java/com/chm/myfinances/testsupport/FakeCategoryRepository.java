package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link CategoryRepository}, shared across application-service tests
 * (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeCategoryRepository implements CategoryRepository {

  private final Map<UUID, Category> store = new HashMap<>();

  @Override
  public Category save(Category category) {
    store.put(category.getId(), category);
    return category;
  }

  @Override
  public Optional<Category> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Category> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return store.containsKey(id);
  }

  @Override
  public boolean existsByName(String name) {
    return store.values().stream().anyMatch(c -> c.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(c -> c.getName().equals(name) && !c.getId().equals(excludedId));
  }
}
