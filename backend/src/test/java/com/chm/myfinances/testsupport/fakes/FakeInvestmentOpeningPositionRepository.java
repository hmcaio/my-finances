package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentOpeningPositionRepository} (F038), shared across
 * application-layer tests. Does not enforce the {@code UNIQUE (holding_id)} constraint - the
 * service replaces the holding's existing position and the real adapter test covers the constraint
 * itself.
 */
public final class FakeInvestmentOpeningPositionRepository
    extends InMemoryRepository<InvestmentOpeningPosition>
    implements InvestmentOpeningPositionRepository {

  public FakeInvestmentOpeningPositionRepository() {
    super(InvestmentOpeningPosition::getId);
  }

  @Override
  public Optional<InvestmentOpeningPosition> findByHoldingId(UUID holdingId) {
    return values().stream().filter(p -> p.getHoldingId().equals(holdingId)).findFirst();
  }

  @Override
  public List<InvestmentOpeningPosition> findByHoldingIds(Collection<UUID> holdingIds) {
    return values().stream().filter(p -> holdingIds.contains(p.getHoldingId())).toList();
  }

  @Override
  public boolean existsByHoldingId(UUID holdingId) {
    return values().stream().anyMatch(p -> p.getHoldingId().equals(holdingId));
  }
}
