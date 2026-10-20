package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link PaymentMethodRepositoryAdapter}, against a real
 * ephemeral Postgres via Testcontainers (ADR 0010). See {@code CategoryRepositoryAdapterTest} for
 * why this uses full {@code @SpringBootTest} rather than {@code @DataJpaTest} (removed in Spring
 * Boot 4.x).
 */
@DatabaseIntegrationTest
class PaymentMethodRepositoryAdapterTest {

  @Autowired private PaymentMethodRepository paymentMethodRepository;

  @Test
  void savesAndReloadsAPaymentMethod() {
    PaymentMethod paymentMethod =
        TestFixtures.paymentMethod(paymentMethodRepository, "Debit Card Test");

    Optional<PaymentMethod> reloaded = paymentMethodRepository.findById(paymentMethod.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Debit Card Test");
  }

  @Test
  void savingAgainWithTheSameIdUpdatesTheRow() {
    // F034/ADR 0028: rename() was deleted (the row is now fixed content) - this still proves
    // save() is an upsert-by-id, re-saving a freshly reconstituted instance for the same id.
    PaymentMethod paymentMethod = TestFixtures.paymentMethod(paymentMethodRepository, "Original");

    paymentMethodRepository.save(PaymentMethod.reconstitute(paymentMethod.getId(), "Renamed"));

    Optional<PaymentMethod> reloaded = paymentMethodRepository.findById(paymentMethod.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
  }

  @Test
  void starterPaymentMethodsFromTheMigrationAreSeeded() {
    List<PaymentMethod> all = paymentMethodRepository.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .contains("Debit Card", "Credit Card", "PIX", "Cash");
  }
}
