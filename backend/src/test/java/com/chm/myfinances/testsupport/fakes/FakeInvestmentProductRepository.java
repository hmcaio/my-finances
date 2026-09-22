package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentProductRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentProductRepository extends InMemoryRepository<InvestmentProduct>
    implements InvestmentProductRepository {

  public FakeInvestmentProductRepository() {
    super(InvestmentProduct::getId);
  }

  @Override
  public List<InvestmentProduct> findByAccountId(UUID accountId) {
    return values().stream().filter(p -> p.getAccountId().equals(accountId)).toList();
  }

  @Override
  public boolean existsByAccountIdAndName(UUID accountId, String name) {
    return values().stream()
        .anyMatch(p -> p.getAccountId().equals(accountId) && p.getName().equals(name));
  }

  @Override
  public boolean existsByAccountIdAndNameAndIdNot(UUID accountId, String name, UUID excludedId) {
    return values().stream()
        .anyMatch(
            p ->
                p.getAccountId().equals(accountId)
                    && p.getName().equals(name)
                    && !p.getId().equals(excludedId));
  }

  @Override
  public boolean existsOpenByAccountId(UUID accountId) {
    return values().stream().anyMatch(p -> p.getAccountId().equals(accountId) && !p.isClosed());
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
}
