package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link AccountRepository}, shared across application-service tests
 * (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeAccountRepository extends InMemoryRepository<Account>
    implements AccountRepository {

  public FakeAccountRepository() {
    super(Account::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(a -> a.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(a -> a.getName().equals(name) && !a.getId().equals(excludedId));
  }

  @Override
  public boolean existsByInstitutionId(UUID institutionId) {
    return values().stream().anyMatch(a -> a.getInstitutionId().equals(institutionId));
  }
}
