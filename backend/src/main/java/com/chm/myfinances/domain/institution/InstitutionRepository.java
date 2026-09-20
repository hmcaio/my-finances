package com.chm.myfinances.domain.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Institution} (ADR 0004: domain/application logic sits behind ports,
 * isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/institution}.
 */
public interface InstitutionRepository {

  Institution save(Institution institution);

  Optional<Institution> findById(UUID id);

  List<Institution> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether an Institution already has this exact name - backs the create-time duplicate guard. */
  boolean existsByName(String name);

  /**
   * Whether an Institution other than {@code excludedId} already has this exact name - backs the
   * rename-time duplicate guard without rejecting a no-op rename to the institution's own name.
   */
  boolean existsByNameAndIdNot(String name, UUID excludedId);
}
