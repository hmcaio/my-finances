package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-layer integration test for {@link PaymentMethodRepositoryAdapter}, against a real
 * ephemeral Postgres via Testcontainers (ADR 0010). See {@code CategoryRepositoryAdapterTest} for
 * why this uses full {@code @SpringBootTest} rather than {@code @DataJpaTest} (removed in Spring
 * Boot 4.x). The port is read-only (ADR 0028), so rows are inserted with {@link TaxonomySeeder}.
 */
@DatabaseIntegrationTest
class PaymentMethodRepositoryAdapterTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PaymentMethodRepository paymentMethodRepository;

  @Test
  void findsAPaymentMethodById() {
    PaymentMethod paymentMethod = TaxonomySeeder.paymentMethod(jdbcTemplate, "Debit Card Test");

    Optional<PaymentMethod> reloaded = paymentMethodRepository.findById(paymentMethod.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Debit Card Test");
    assertThat(paymentMethodRepository.existsById(paymentMethod.getId())).isTrue();
  }

  @Test
  void starterPaymentMethodsFromTheMigrationAreSeeded() {
    List<PaymentMethod> all = paymentMethodRepository.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .contains("Debit Card", "Credit Card", "PIX", "Cash");
  }
}
