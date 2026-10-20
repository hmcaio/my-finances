package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an {@code InvestmentProduct} id does not resolve to an existing product. Maps to 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentProductNotFoundException extends RuntimeException {

  public InvestmentProductNotFoundException(UUID id) {
    super("Investment product not found: " + id);
  }
}
