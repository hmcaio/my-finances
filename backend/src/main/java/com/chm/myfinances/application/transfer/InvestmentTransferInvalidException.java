package com.chm.myfinances.application.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code Transfer}'s investment shape is inconsistent with the persisted accounts and
 * product (F009 spec): an {@code INVESTMENT} endpoint without a product, a product that doesn't
 * belong to that endpoint's account, a product without an {@code INVESTMENT} endpoint, or two
 * {@code INVESTMENT} endpoints (a transfer between two investment accounts isn't modeled). Maps to
 * 409 - each case depends on account/product state, unlike the request-shape errors the DTO turns
 * into 400.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentTransferInvalidException extends RuntimeException {

  public InvestmentTransferInvalidException(String reason) {
    super("Invalid investment transfer: " + reason);
  }
}
