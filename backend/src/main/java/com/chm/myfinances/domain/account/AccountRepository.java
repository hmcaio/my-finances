package com.chm.myfinances.domain.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Account} (ADR 0004: domain/application logic sits behind ports,
 * isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/account}.
 *
 * <p>{@link #deleteById} is only for an account with no history (ADR 0017); everything else is
 * closed, never deleted.
 */
public interface AccountRepository {

  Account save(Account account);

  Optional<Account> findById(UUID id);

  List<Account> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether an Account already has this exact name - backs the create-time duplicate guard. */
  boolean existsByName(String name);

  /**
   * Whether an Account other than {@code excludedId} already has this exact name - backs the
   * edit-time duplicate guard without rejecting a no-op edit that keeps the account's own current
   * name.
   */
  boolean existsByNameAndIdNot(String name, UUID excludedId);

  /**
   * Whether any Account, open or closed, references this institution - backs {@code
   * InstitutionService}'s delete guard. Closed accounts count: a closed account is only deletable
   * once it has no history.
   */
  boolean existsByInstitutionId(UUID institutionId);
}
