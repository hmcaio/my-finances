package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link InvestmentSegmentRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010), so {@code V20} runs for real (F026 spec).
 */
@DatabaseIntegrationTest
class InvestmentSegmentRepositoryAdapterTest {

  @Autowired private InvestmentSegmentRepository segmentRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsASegment() {
    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Shoppings Test");

    segmentRepository.save(segment);

    Optional<InvestmentSegment> reloaded = segmentRepository.findById(segment.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Shoppings Test");
  }

  @Test
  void renamePersists() {
    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Original Test");
    segmentRepository.save(segment);

    segment.rename("Renamed Test");
    segmentRepository.save(segment);

    assertThat(segmentRepository.findById(segment.getId()).orElseThrow().getName())
        .isEqualTo("Renamed Test");
  }

  @Test
  void deleteRemovesTheSegment() {
    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Temp Test");
    segmentRepository.save(segment);

    segmentRepository.deleteById(segment.getId());

    assertThat(segmentRepository.existsById(segment.getId())).isFalse();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    segmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Unique Name Test"));

    assertThat(segmentRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(segmentRepository.existsByName("unique name test")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    InvestmentSegment segment =
        segmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Exclude Self Test"));

    assertThat(segmentRepository.existsByNameAndIdNot("Exclude Self Test", segment.getId()))
        .isFalse();
    assertThat(segmentRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void theDatabaseRejectsADuplicateNameEvenIfTheServiceCheckIsBypassed() {
    segmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Twice Test"));
    entityManager.flush();

    segmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Twice Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_segments_name");
  }
}
