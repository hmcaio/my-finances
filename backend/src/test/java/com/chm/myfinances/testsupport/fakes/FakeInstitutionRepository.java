package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link InstitutionRepository}, shared across application-service tests
 * (same spirit as {@link FakeCategoryRepository}).
 */
public final class FakeInstitutionRepository extends InMemoryRepository<Institution>
    implements InstitutionRepository {

  public FakeInstitutionRepository() {
    super(Institution::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(i -> i.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(i -> i.getName().equals(name) && !i.getId().equals(excludedId));
  }
}
