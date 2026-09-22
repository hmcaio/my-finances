package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link CategoryRepository}, shared across application-service tests
 * (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeCategoryRepository extends InMemoryRepository<Category>
    implements CategoryRepository {

  public FakeCategoryRepository() {
    super(Category::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(c -> c.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(c -> c.getName().equals(name) && !c.getId().equals(excludedId));
  }
}
