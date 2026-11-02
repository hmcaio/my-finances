package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-layer integration test for {@link InvestmentSegmentRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010), so {@code V20} runs for real (F026 spec). The port is
 * read-only (ADR 0028), so rows are inserted with {@link TaxonomySeeder}.
 */
@DatabaseIntegrationTest
class InvestmentSegmentRepositoryAdapterTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvestmentSegmentRepository segmentRepository;

  @Test
  void findsASegmentById() {
    InvestmentSegment segment = TaxonomySeeder.investmentSegment(jdbcTemplate, "Shoppings Test");

    Optional<InvestmentSegment> reloaded = segmentRepository.findById(segment.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Shoppings Test");
    assertThat(segmentRepository.existsById(segment.getId())).isTrue();
  }

  @Test
  void theMigrationSeedsTheStarterSegments() {
    assertThat(segmentRepository.findAll())
        .extracting(InvestmentSegment::getName)
        .contains("Shoppings");
  }

  @Test
  void theDatabaseRejectsADuplicateName() {
    TaxonomySeeder.investmentSegment(jdbcTemplate, "Twice Test");

    assertThatThrownBy(() -> TaxonomySeeder.investmentSegment(jdbcTemplate, "Twice Test"))
        .hasStackTraceContaining("uq_investment_segments_name");
  }
}
