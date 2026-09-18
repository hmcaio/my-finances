package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link PaymentMethodRepository} port on top of Spring
 * Data/Hibernate (ADR 0004).
 */
@Component
public class PaymentMethodRepositoryAdapter implements PaymentMethodRepository {

  private final PaymentMethodJpaRepository jpaRepository;

  public PaymentMethodRepositoryAdapter(PaymentMethodJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public PaymentMethod save(PaymentMethod paymentMethod) {
    PaymentMethodJpaEntity entity =
        jpaRepository
            .findById(paymentMethod.getId())
            .map(
                existing -> {
                  existing.setName(paymentMethod.getName());
                  return existing;
                })
            .orElseGet(
                () -> new PaymentMethodJpaEntity(paymentMethod.getId(), paymentMethod.getName()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<PaymentMethod> findById(UUID id) {
    return jpaRepository.findById(id).map(PaymentMethodRepositoryAdapter::toDomain);
  }

  @Override
  public List<PaymentMethod> findAll() {
    return jpaRepository.findAll().stream().map(PaymentMethodRepositoryAdapter::toDomain).toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return jpaRepository.existsById(id);
  }

  @Override
  public boolean existsByName(String name) {
    return jpaRepository.existsByName(name);
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return jpaRepository.existsByNameAndIdNot(name, excludedId);
  }

  private static PaymentMethod toDomain(PaymentMethodJpaEntity entity) {
    return PaymentMethod.reconstitute(entity.getId(), entity.getName());
  }
}
