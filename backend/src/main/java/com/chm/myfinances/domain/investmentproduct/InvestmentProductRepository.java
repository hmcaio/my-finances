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

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /**
   * Whether a product already has this exact name (create guard, F022: name is globally unique).
   */
  boolean existsByName(String name);

  /**
   * Whether a product other than {@code excludedId} has this exact name (edit guard, tolerant of a
   * no-op edit).
   */
  boolean existsByNameAndIdNot(String name, UUID excludedId);

  /** Whether any product is classified under this category - backs the category delete guard. */
  boolean existsByInvestmentCategoryId(UUID investmentCategoryId);

  /** Whether any product uses this sub-category - backs the sub-category delete guard. */
  boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId);

  /** Whether any product uses this segment (F026) - backs the segment delete guard. */
  boolean existsBySegmentId(UUID segmentId);
}
