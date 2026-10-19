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
}
