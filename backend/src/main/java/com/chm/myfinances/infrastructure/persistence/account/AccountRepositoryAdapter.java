package com.chm.myfinances.infrastructure.persistence.account;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link AccountRepository} port on top of Spring Data/ Hibernate
 * (ADR 0004). Translates between the framework-free {@link Account} aggregate and {@link
 * AccountJpaEntity}.
 */
@Component
public class AccountRepositoryAdapter implements AccountRepository {

  private final AccountJpaRepository jpaRepository;

  public AccountRepositoryAdapter(AccountJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Account save(Account account) {
    AccountJpaEntity entity =
        jpaRepository
            .findById(account.getId())
            .map(
                existing -> {
                  existing.setName(account.getName());
                  existing.setInstitution(account.getInstitution());
                  existing.setClosedDate(account.getClosedDate());
                  return existing;
                })
            .orElseGet(
                () ->
                    new AccountJpaEntity(
                        account.getId(),
                        account.getName(),
                        account.getInstitution(),
                        account.getType(),
                        account.getOpeningBalance(),
                        account.getOpeningBalanceDate(),
                        account.getClosedDate()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Account> findById(UUID id) {
    return jpaRepository.findById(id).map(AccountRepositoryAdapter::toDomain);
  }

  @Override
  public List<Account> findAll() {
    return jpaRepository.findAll().stream().map(AccountRepositoryAdapter::toDomain).toList();
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

  private static Account toDomain(AccountJpaEntity entity) {
    return Account.reconstitute(
        entity.getId(),
        entity.getName(),
        entity.getInstitution(),
        entity.getType(),
        entity.getOpeningBalance(),
        entity.getOpeningBalanceDate(),
        entity.getClosedDate());
  }
}
