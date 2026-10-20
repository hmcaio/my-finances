package com.chm.myfinances.application.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.testsupport.fakes.FakePaymentMethodRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link PaymentMethodService}, against hand-written fakes — plain
 * JUnit, no Spring context (ADR 0004).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were deleted — every row is now
 * fixed, migration-seeded content, so only {@code findAll} is left to cover. Fixtures are saved
 * directly through the repository via {@link PaymentMethod#reconstitute}.
 */
class PaymentMethodServiceTest {

  private final FakePaymentMethodRepository repository = new FakePaymentMethodRepository();
  private final PaymentMethodService service = new PaymentMethodService(repository);

  @Test
  void findAllReturnsEveryPersistedPaymentMethod() {
    repository.save(PaymentMethod.reconstitute(UUID.randomUUID(), "Debit Card"));
    repository.save(PaymentMethod.reconstitute(UUID.randomUUID(), "Cash"));

    List<PaymentMethod> all = service.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .containsExactlyInAnyOrder("Debit Card", "Cash");
  }

  @Test
  void findAllReturnsEmptyListWhenNoPaymentMethodsExist() {
    assertThat(service.findAll()).isEmpty();
  }
}
