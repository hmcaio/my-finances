package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentHoldingRepository}, shared across application-service
 * tests (F022, same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentHoldingRepository extends InMemoryRepository<InvestmentHolding>
    implements InvestmentHoldingRepository {

  public FakeInvestmentHoldingRepository() {
    super(InvestmentHolding::getId);
  }

  @Override
  public List<InvestmentHolding> findByProductId(UUID productId) {
    return values().stream().filter(h -> h.getProductId().equals(productId)).toList();
  }

  @Override
  public List<InvestmentHolding> findByAccountId(UUID accountId) {
    return values().stream().filter(h -> h.getAccountId().equals(accountId)).toList();
  }

  @Override
  public Optional<InvestmentHolding> findByProductIdAndAccountId(UUID productId, UUID accountId) {
    return values().stream()
        .filter(h -> h.getProductId().equals(productId) && h.getAccountId().equals(accountId))
        .findFirst();
  }

  @Override
  public boolean existsByProductIdAndAccountId(UUID productId, UUID accountId) {
    return findByProductIdAndAccountId(productId, accountId).isPresent();
  }

  @Override
  public boolean existsByProductId(UUID productId) {
    return values().stream().anyMatch(h -> h.getProductId().equals(productId));
  }

  @Override
  public boolean existsByAccountId(UUID accountId) {
    return values().stream().anyMatch(h -> h.getAccountId().equals(accountId));
  }

  @Override
  public boolean existsOpenByAccountId(UUID accountId) {
    return values().stream().anyMatch(h -> h.getAccountId().equals(accountId) && !h.isClosed());
  }
}
