package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentProductRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentProductRepository implements InvestmentProductRepository {

  private final Set<UUID> accountsWithAnOpenProduct = new HashSet<>();

  /** Test seam until the port grows real product persistence. */
  public void addOpenProductTo(UUID accountId) {
    accountsWithAnOpenProduct.add(accountId);
  }

  public void closeAllProductsOf(UUID accountId) {
    accountsWithAnOpenProduct.remove(accountId);
  }

  @Override
  public boolean existsOpenByAccountId(UUID accountId) {
    return accountsWithAnOpenProduct.contains(accountId);
  }
}
