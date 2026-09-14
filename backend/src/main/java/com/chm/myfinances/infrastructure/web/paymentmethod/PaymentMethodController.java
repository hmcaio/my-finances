package com.chm.myfinances.infrastructure.web.paymentmethod;

import com.chm.myfinances.application.paymentmethod.PaymentMethodService;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code PaymentMethod} (F002 spec). */
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentMethodResponse create(@Valid @RequestBody CreatePaymentMethodRequest request) {
    PaymentMethod paymentMethod = paymentMethodService.create(request.name());
    return PaymentMethodResponse.from(paymentMethod);
  }

  @PatchMapping("/{id}")
  public PaymentMethodResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdatePaymentMethodRequest request) {
    PaymentMethod paymentMethod = paymentMethodService.rename(id, request.name());
    return PaymentMethodResponse.from(paymentMethod);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    paymentMethodService.delete(id);
  }
}
