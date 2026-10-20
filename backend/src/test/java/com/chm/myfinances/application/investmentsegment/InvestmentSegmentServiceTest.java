package com.chm.myfinances.application.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentSegmentService}, against hand-written fakes - plain
 * JUnit, no Spring context (F026 spec).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were deleted - every row is now
 * fixed, migration-seeded content, so only {@code findAll} is left to cover. Fixtures are saved
 * directly through the repository via {@link InvestmentSegment#reconstitute}.
 */
class InvestmentSegmentServiceTest {

  private final FakeInvestmentSegmentRepository segmentRepository =
      new FakeInvestmentSegmentRepository();
  private final InvestmentSegmentService service = new InvestmentSegmentService(segmentRepository);

  @Test
  void findAllReturnsEverySegment() {
    segmentRepository.save(InvestmentSegment.reconstitute(UUID.randomUUID(), "Shoppings"));
    segmentRepository.save(InvestmentSegment.reconstitute(UUID.randomUUID(), "Logistica"));

    assertThat(service.findAll()).hasSize(2);
  }

  @Test
  void findAllReturnsEmptyListWhenNoSegmentsExist() {
    assertThat(service.findAll()).isEmpty();
  }
}
