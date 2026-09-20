package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InstitutionRepository}, shared across application-service tests
 * (same spirit as {@link FakeCategoryRepository}).
 */
public final class FakeInstitutionRepository implements InstitutionRepository {

  private final Map<UUID, Institution> store = new HashMap<>();

  @Override
  public Institution save(Institution institution) {
    store.put(institution.getId(), institution);
    return institution;
  }

  @Override
  public Optional<Institution> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Institution> findAll() {
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
    return store.values().stream().anyMatch(i -> i.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(i -> i.getName().equals(name) && !i.getId().equals(excludedId));
  }
}
