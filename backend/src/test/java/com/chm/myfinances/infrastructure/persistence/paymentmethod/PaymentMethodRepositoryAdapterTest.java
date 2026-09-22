package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  void renamePersists() {
    PaymentMethod paymentMethod = TestFixtures.paymentMethod(paymentMethodRepository, "Original");

    paymentMethod.rename("Renamed");
    paymentMethodRepository.save(paymentMethod);

    Optional<PaymentMethod> reloaded = paymentMethodRepository.findById(paymentMethod.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
  }

  @Test
  void deleteRemovesThePaymentMethod() {
    PaymentMethod paymentMethod = TestFixtures.paymentMethod(paymentMethodRepository, "Temp");

    paymentMethodRepository.deleteById(paymentMethod.getId());

    assertThat(paymentMethodRepository.existsById(paymentMethod.getId())).isFalse();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    TestFixtures.paymentMethod(paymentMethodRepository, "Unique Name Test");

    assertThat(paymentMethodRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(paymentMethodRepository.existsByName("unique name test")).isFalse();
    assertThat(paymentMethodRepository.existsByName("Something Else")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    PaymentMethod paymentMethod =
        TestFixtures.paymentMethod(paymentMethodRepository, "Exclude Self Test");

    assertThat(
            paymentMethodRepository.existsByNameAndIdNot(
                "Exclude Self Test", paymentMethod.getId()))
        .isFalse();
    assertThat(paymentMethodRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void starterPaymentMethodsFromTheMigrationAreSeeded() {
    List<PaymentMethod> all = paymentMethodRepository.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .contains("Debit Card", "Credit Card", "PIX", "Cash");
  }
}
