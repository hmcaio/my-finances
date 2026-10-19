package com.chm.myfinances.infrastructure.web.ledger;

import com.chm.myfinances.application.ledger.LedgerSummary;
import java.math.BigDecimal;

/** API representation of {@link LedgerSummary} (F037): income, expense and net of the scope. */
public record LedgerSummaryResponse(BigDecimal income, BigDecimal expense, BigDecimal net) {

  public static LedgerSummaryResponse from(LedgerSummary summary) {
    return new LedgerSummaryResponse(summary.income(), summary.expense(), summary.net());
  }
}
