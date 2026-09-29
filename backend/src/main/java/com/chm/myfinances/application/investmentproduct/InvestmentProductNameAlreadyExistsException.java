package com.chm.myfinances.application.investmentproduct;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or editing an {@code InvestmentProduct} to a name another product already
 * has. Globally unique since F022/ADR 0020: the scenario per-account uniqueness protected (the same
 * instrument at two brokers) is now one product with two holdings, so two *different* instruments
 * sharing a name is the only remaining collision - same as every other flat-taxonomy entity. Maps
 * to 409; backed by {@code UNIQUE (name)}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductNameAlreadyExistsException extends RuntimeException {

  public InvestmentProductNameAlreadyExistsException(String name) {
    super("An investment product named '" + name + "' already exists");
  }
}
