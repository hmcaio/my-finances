package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link AccountRepository}, shared across application-service tests
 * (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeAccountRepository implements AccountRepository {

  private final Map<UUID, Account> store = new HashMap<>();

  @Override
  public Account save(Account account) {
    store.put(account.getId(), account);
    return account;
  }

  @Override
  public Optional<Account> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Account> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public boolean existsById(UUID id) {
    return store.containsKey(id);
  }

  @Override
  public boolean existsByName(String name) {
    return store.values().stream().anyMatch(a -> a.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(a -> a.getName().equals(name) && !a.getId().equals(excludedId));
  }
}
