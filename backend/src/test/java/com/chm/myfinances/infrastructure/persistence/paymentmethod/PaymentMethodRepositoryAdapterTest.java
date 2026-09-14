package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration test for {@link PaymentMethodRepositoryAdapter}, against a real
 * ephemeral Postgres via Testcontainers (ADR 0010). See {@code CategoryRepositoryAdapterTest} for
 * why this uses full {@code @SpringBootTest} rather than {@code @DataJpaTest} (removed in Spring
 * Boot 4.x).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentMethodRepositoryAdapterTest {

  @Autowired private PaymentMethodRepository paymentMethodRepository;

  @Test
  void savesAndReloadsAPaymentMethod() {
    PaymentMethod paymentMethod = PaymentMethod.create(UUID.randomUUID(), "Debit Card Test");

    paymentMethodRepository.save(paymentMethod);

    Optional<PaymentMethod> reloaded = paymentMethodRepository.findById(paymentMethod.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Debit Card Test");
  }

  @Test
  void renamePersists() {
    PaymentMethod paymentMethod = PaymentMethod.create(UUID.randomUUID(), "Original");
    paymentMethodRepository.save(paymentMethod);

    paymentMethod.rename("Renamed");
    paymentMethodRepository.save(paymentMethod);

    Optional<PaymentMethod> reloaded = paymentMethodRepository.findById(paymentMethod.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
  }

  @Test
  void deleteRemovesThePaymentMethod() {
    PaymentMethod paymentMethod = PaymentMethod.create(UUID.randomUUID(), "Temp");
    paymentMethodRepository.save(paymentMethod);

    paymentMethodRepository.deleteById(paymentMethod.getId());

    assertThat(paymentMethodRepository.existsById(paymentMethod.getId())).isFalse();
  }

  @Test
  void starterPaymentMethodsFromTheMigrationAreSeeded() {
    List<PaymentMethod> all = paymentMethodRepository.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .contains("Debit Card", "Credit Card", "PIX", "Cash");
  }
}
