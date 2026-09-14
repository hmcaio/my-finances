package com.chm.myfinances.domain.paymentmethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link PaymentMethod} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/paymentmethod}.
 */
public interface PaymentMethodRepository {

  PaymentMethod save(PaymentMethod paymentMethod);

  Optional<PaymentMethod> findById(UUID id);

  List<PaymentMethod> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);
}
