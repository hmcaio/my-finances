package com.chm.myfinances.domain.investmentproduct;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentProduct} (ADR 0004: domain/application logic sits behind
 * ports, isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentproduct}.
 */
public interface InvestmentProductRepository {

  InvestmentProduct save(InvestmentProduct product);

  Optional<InvestmentProduct> findById(UUID id);

  List<InvestmentProduct> findAll();

  List<InvestmentProduct> findByAccountId(UUID accountId);

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether a product already has this exact name inside the account (create guard). */
  boolean existsByAccountIdAndName(UUID accountId, String name);

  /**
   * Whether a product other than {@code excludedId} inside the account has this exact name (edit
   * guard, tolerant of a no-op edit).
   */
  boolean existsByAccountIdAndNameAndIdNot(UUID accountId, String name, UUID excludedId);

  /**
   * Whether the account owns at least one product that isn't closed - backs {@code
   * AccountService.close}'s rule that an {@code INVESTMENT} account can only be closed once all its
   * products are (F008 spec).
   */
  boolean existsOpenByAccountId(UUID accountId);

  /** Whether the account owns any product, open or closed - part of the account delete guard. */
  boolean existsByAccountId(UUID accountId);

  /** Whether any product is classified under this category - backs the category delete guard. */
  boolean existsByInvestmentCategoryId(UUID investmentCategoryId);

  /** Whether any product uses this sub-category - backs the sub-category delete guard. */
  boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId);
}
