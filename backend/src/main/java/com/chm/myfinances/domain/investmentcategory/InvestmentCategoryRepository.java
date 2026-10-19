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

  boolean existsById(UUID id);
}
