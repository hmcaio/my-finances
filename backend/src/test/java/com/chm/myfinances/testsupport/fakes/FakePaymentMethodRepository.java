package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;

/**
 * In-memory test double for {@link PaymentMethodRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakePaymentMethodRepository extends InMemoryRepository<PaymentMethod>
    implements PaymentMethodRepository {

  public FakePaymentMethodRepository() {
    super(PaymentMethod::getId);
  }
}
