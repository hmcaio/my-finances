package com.chm.myfinances.application.investmentsegment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown on a create/rename that would duplicate an existing segment's name (F026). */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSegmentNameAlreadyExistsException extends RuntimeException {

  public InvestmentSegmentNameAlreadyExistsException(String name) {
    super("Investment segment name already exists: " + name);
  }
}
