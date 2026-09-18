package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link PaymentMethodRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakePaymentMethodRepository implements PaymentMethodRepository {

  private final Map<UUID, PaymentMethod> store = new HashMap<>();

  @Override
  public PaymentMethod save(PaymentMethod paymentMethod) {
    store.put(paymentMethod.getId(), paymentMethod);
    return paymentMethod;
  }

  @Override
  public Optional<PaymentMethod> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<PaymentMethod> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return store.containsKey(id);
  }

  @Override
  public boolean existsByName(String name) {
    return store.values().stream().anyMatch(p -> p.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(p -> p.getName().equals(name) && !p.getId().equals(excludedId));
  }
}
