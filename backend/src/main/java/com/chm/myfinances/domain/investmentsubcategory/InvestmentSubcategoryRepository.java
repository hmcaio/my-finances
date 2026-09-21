package com.chm.myfinances.domain.investmentsubcategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentSubcategory} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentsubcategory}. Names are unique per parent category, not
 * globally ("ETFs" exists under both Variable Income and International).
 */
public interface InvestmentSubcategoryRepository {

  InvestmentSubcategory save(InvestmentSubcategory subcategory);

  Optional<InvestmentSubcategory> findById(UUID id);

  List<InvestmentSubcategory> findAll();

  void deleteById(UUID id);

  /** Whether the category still has at least one sub-category - backs its delete guard. */
  boolean existsByInvestmentCategoryId(UUID investmentCategoryId);

  /** Whether the category already has a sub-category with this exact name (create guard). */
  boolean existsByInvestmentCategoryIdAndName(UUID investmentCategoryId, String name);

  /**
   * Whether the category has a sub-category other than {@code excludedId} with this exact name
   * (rename guard, tolerant of a no-op rename).
   */
  boolean existsByInvestmentCategoryIdAndNameAndIdNot(
      UUID investmentCategoryId, String name, UUID excludedId);
}
