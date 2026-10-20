package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentProductRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}). F022: pure taxonomy, name globally unique.
 */
public final class FakeInvestmentProductRepository extends InMemoryRepository<InvestmentProduct>
    implements InvestmentProductRepository {

  public FakeInvestmentProductRepository() {
    super(InvestmentProduct::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(p -> p.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(p -> p.getName().equals(name) && !p.getId().equals(excludedId));
  }

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return values().stream()
        .anyMatch(p -> p.getInvestmentCategoryId().equals(investmentCategoryId));
  }

  @Override
  public boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId) {
    return values().stream()
        .anyMatch(p -> Objects.equals(p.getInvestmentSubcategoryId(), investmentSubcategoryId));
  }

  @Override
  public boolean existsBySegmentId(UUID segmentId) {
    return values().stream().anyMatch(p -> Objects.equals(p.getSegmentId(), segmentId));
  }
}
