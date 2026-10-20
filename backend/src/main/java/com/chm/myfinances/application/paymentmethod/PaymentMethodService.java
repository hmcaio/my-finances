package com.chm.myfinances.application.paymentmethod;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read use case for {@link PaymentMethod} (PRD S5.2, F002 spec).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were deleted — every row is now
 * fixed, migration-seeded content (today's four: Debit Card, Credit Card, PIX, Cash), unchanged.
 */
@Service
public class PaymentMethodService {

  private final PaymentMethodRepository paymentMethodRepository;

  public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
    this.paymentMethodRepository = paymentMethodRepository;
  }

  public List<PaymentMethod> findAll() {
    return paymentMethodRepository.findAll();
  }
}
