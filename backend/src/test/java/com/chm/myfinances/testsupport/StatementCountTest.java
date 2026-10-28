package com.chm.myfinances.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.institution.InstitutionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pins that {@link StatementCount} counts: a helper that silently returned {@code 0} would make
 * every "at most N statements" assertion built on it pass without checking anything.
 */
@DatabaseIntegrationTest
class StatementCountTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void countsEachStatementTheReadSends() {
    long none = StatementCount.of(entityManager, () -> {});
    long one = StatementCount.of(entityManager, institutionRepository::findAll);
    long two =
        StatementCount.of(
            entityManager,
            () -> {
              institutionRepository.findAll();
              institutionRepository.findAll();
            });

    assertThat(none).isZero();
    assertThat(one).isEqualTo(1);
    assertThat(two).isEqualTo(2);
  }
}
