package com.chm.myfinances.domain.investmentcategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentCategory} (ADR 0004: domain/application logic sits behind
 * ports, isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentcategory}.
 */
public interface InvestmentCategoryRepository {

  InvestmentCategory save(InvestmentCategory category);

  Optional<InvestmentCategory> findById(UUID id);

  List<InvestmentCategory> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether a category already has this exact name - backs the create-time duplicate guard. */
  boolean existsByName(String name);

  /**
   * Whether a category other than {@code excludedId} already has this exact name - backs the
   * rename-time duplicate guard without rejecting a no-op rename to its own current name.
   */
  boolean existsByNameAndIdNot(String name, UUID excludedId);
}
