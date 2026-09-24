package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.application.transaction.CategorySpend;
import java.math.BigDecimal;
import java.util.UUID;

/** API representation of one {@link CategorySpend} (F012's monthly spend by category). */
public record CategorySpendResponse(UUID categoryId, BigDecimal total) {

  public static CategorySpendResponse from(CategorySpend spend) {
    return new CategorySpendResponse(spend.categoryId(), spend.total());
  }
}
