package com.chm.myfinances.infrastructure.web.paymentmethod;

import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code PaymentMethod} (F002 spec).
 *
 * <p>F034/ADR 0028: read-only — {@code POST}/{@code PATCH}/{@code DELETE} were removed, every row
 * is fixed, migration-seeded content. With no rule left to apply there is no use-case service: the
 * controller reads the port directly (docs/conventions/backend-structure.md).
 */
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

  private final PaymentMethodRepository paymentMethodRepository;

  public PaymentMethodController(PaymentMethodRepository paymentMethodRepository) {
    this.paymentMethodRepository = paymentMethodRepository;
  }

  @GetMapping
  public List<PaymentMethodResponse> list() {
    return paymentMethodRepository.findAll().stream().map(PaymentMethodResponse::from).toList();
  }
}
