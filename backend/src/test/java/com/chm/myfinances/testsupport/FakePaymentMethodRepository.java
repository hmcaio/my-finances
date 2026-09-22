package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link PaymentMethodRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakePaymentMethodRepository extends InMemoryRepository<PaymentMethod>
    implements PaymentMethodRepository {

  public FakePaymentMethodRepository() {
    super(PaymentMethod::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(p -> p.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(p -> p.getName().equals(name) && !p.getId().equals(excludedId));
  }
}
