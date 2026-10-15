package com.chm.myfinances.infrastructure.web.paymentmethod;

import com.chm.myfinances.application.paymentmethod.PaymentMethodService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code PaymentMethod} (F002 spec).
 *
 * <p>F034/ADR 0028: read-only — {@code POST}/{@code PATCH}/{@code DELETE} were removed, every row
 * is fixed, migration-seeded content.
 */
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

  private final PaymentMethodService paymentMethodService;

  public PaymentMethodController(PaymentMethodService paymentMethodService) {
    this.paymentMethodService = paymentMethodService;
  }

  @GetMapping
  public List<PaymentMethodResponse> list() {
    return paymentMethodService.findAll().stream().map(PaymentMethodResponse::from).toList();
  }
}
