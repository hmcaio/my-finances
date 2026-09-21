package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentProductRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentProductRepository implements InvestmentProductRepository {

  private final Map<UUID, InvestmentProduct> store = new HashMap<>();

  @Override
  public InvestmentProduct save(InvestmentProduct product) {
    store.put(product.getId(), product);
    return product;
  }

  @Override
  public Optional<InvestmentProduct> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<InvestmentProduct> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public List<InvestmentProduct> findByAccountId(UUID accountId) {
    return store.values().stream().filter(p -> p.getAccountId().equals(accountId)).toList();
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public boolean existsByAccountIdAndName(UUID accountId, String name) {
    return store.values().stream()
        .anyMatch(p -> p.getAccountId().equals(accountId) && p.getName().equals(name));
  }

  @Override
  public boolean existsByAccountIdAndNameAndIdNot(UUID accountId, String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(
            p ->
                p.getAccountId().equals(accountId)
                    && p.getName().equals(name)
                    && !p.getId().equals(excludedId));
  }

  @Override
  public boolean existsOpenByAccountId(UUID accountId) {
    return store.values().stream()
        .anyMatch(p -> p.getAccountId().equals(accountId) && !p.isClosed());
  }

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return store.values().stream()
        .anyMatch(p -> p.getInvestmentCategoryId().equals(investmentCategoryId));
  }

  @Override
  public boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId) {
    return store.values().stream()
        .anyMatch(p -> Objects.equals(p.getInvestmentSubcategoryId(), investmentSubcategoryId));
  }
}
