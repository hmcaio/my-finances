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
 * tests. Does not enforce the {@code UNIQUE (product_id, date)} constraint - the service upserts on
 * that pair and the real adapter test covers the constraint itself.
 */
public final class FakeInvestmentSnapshotRepository extends InMemoryRepository<InvestmentSnapshot>
    implements InvestmentSnapshotRepository {

  public FakeInvestmentSnapshotRepository() {
    super(InvestmentSnapshot::getId);
  }

  @Override
  public Optional<InvestmentSnapshot> findByProductIdAndDate(UUID productId, LocalDate date) {
    return values().stream()
        .filter(s -> s.getProductId().equals(productId) && s.getDate().equals(date))
        .findFirst();
  }

  @Override
  public List<InvestmentSnapshot> findByProductId(UUID productId) {
    return values().stream()
        .filter(s -> s.getProductId().equals(productId))
        .sorted(Comparator.comparing(InvestmentSnapshot::getDate).reversed())
        .toList();
  }

  @Override
  public List<InvestmentSnapshot> findAllOnOrBefore(LocalDate asOfDate) {
    return values().stream().filter(s -> !s.getDate().isAfter(asOfDate)).toList();
  }

  @Override
  public boolean existsByProductId(UUID productId) {
    return values().stream().anyMatch(s -> s.getProductId().equals(productId));
  }
}
