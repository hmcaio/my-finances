package com.chm.myfinances.application.investmentproduct;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or editing an {@code InvestmentProduct} to a name another product in the
 * same account already has - unique per account, since the same instrument held at two brokers is
 * normal (F008 spec). Maps to 409; backed by {@code UNIQUE (account_id, name)}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductNameAlreadyExistsException extends RuntimeException {

  public InvestmentProductNameAlreadyExistsException(String name) {
    super("An investment product named '" + name + "' already exists in this account");
  }
}
