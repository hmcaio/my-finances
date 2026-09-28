package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentSnapshotRepository}, shared across application-layer
 * tests. Rekeyed by holding (F022). Does not enforce the {@code UNIQUE (holding_id, date)}
 * constraint - the service upserts on that pair and the real adapter test covers the constraint
 * itself.
 */
public final class FakeInvestmentSnapshotRepository extends InMemoryRepository<InvestmentSnapshot>
    implements InvestmentSnapshotRepository {

  public FakeInvestmentSnapshotRepository() {
    super(InvestmentSnapshot::getId);
  }

  @Override
  public Optional<InvestmentSnapshot> findByHoldingIdAndDate(UUID holdingId, LocalDate date) {
    return values().stream()
        .filter(s -> s.getHoldingId().equals(holdingId) && s.getDate().equals(date))
        .findFirst();
  }

  @Override
  public List<InvestmentSnapshot> findByHoldingId(UUID holdingId) {
    return values().stream()
        .filter(s -> s.getHoldingId().equals(holdingId))
        .sorted(Comparator.comparing(InvestmentSnapshot::getDate).reversed())
        .toList();
  }

  @Override
  public List<InvestmentSnapshot> findAllOnOrBefore(LocalDate asOfDate) {
    return values().stream().filter(s -> !s.getDate().isAfter(asOfDate)).toList();
  }

  @Override
  public boolean existsByHoldingId(UUID holdingId) {
    return values().stream().anyMatch(s -> s.getHoldingId().equals(holdingId));
  }
}
