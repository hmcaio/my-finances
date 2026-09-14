package com.chm.myfinances.application.paymentmethod;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link PaymentMethod}: create/rename/delete (F002 spec). New ids come from the
 * {@link IdGenerator} port (ADR 0005).
 *
 * <p>Delete is currently unconditional, same caveat as {@code CategoryService}: F002 plan.md's
 * referenced-by-transaction delete guard is deferred to F004 (Transactions).
 */
@Service
public class PaymentMethodService {

  private final PaymentMethodRepository paymentMethodRepository;
  private final IdGenerator idGenerator;

  public PaymentMethodService(
      PaymentMethodRepository paymentMethodRepository, IdGenerator idGenerator) {
    this.paymentMethodRepository = paymentMethodRepository;
    this.idGenerator = idGenerator;
  }

  public PaymentMethod create(String name) {
    PaymentMethod paymentMethod = PaymentMethod.create(idGenerator.newId(), name);
    return paymentMethodRepository.save(paymentMethod);
  }

  public List<PaymentMethod> findAll() {
    return paymentMethodRepository.findAll();
  }

  public PaymentMethod rename(UUID id, String newName) {
    PaymentMethod paymentMethod =
        paymentMethodRepository
            .findById(id)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    paymentMethod.rename(newName);
    return paymentMethodRepository.save(paymentMethod);
  }

  public void delete(UUID id) {
    if (!paymentMethodRepository.existsById(id)) {
      throw new PaymentMethodNotFoundException(id);
    }
    paymentMethodRepository.deleteById(id);
  }
}
