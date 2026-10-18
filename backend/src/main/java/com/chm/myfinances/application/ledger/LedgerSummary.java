package com.chm.myfinances.application.ledger;

import java.math.BigDecimal;

/**
 * Income, expense and {@code net = income - expense} over the whole filtered scope of the Ledger
 * (F037) - income and expense transactions only; transfers contribute nothing (PRD S5.5).
 */
public record LedgerSummary(BigDecimal income, BigDecimal expense, BigDecimal net) {}
