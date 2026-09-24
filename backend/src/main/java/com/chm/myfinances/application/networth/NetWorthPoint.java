package com.chm.myfinances.application.networth;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Net worth on one date (F010 spec, PRD S5.9), computed on read and never stored. {@code assets} is
 * the sum of checking/savings/cash accounts, {@code investments} the sum of {@code INVESTMENT}
 * accounts and {@code liabilities} the sum of credit card balances owed (a positive amount); {@code
 * netWorth = assets + investments - liabilities}.
 */
public record NetWorthPoint(
    LocalDate date,
    BigDecimal netWorth,
    BigDecimal assets,
    BigDecimal liabilities,
    BigDecimal investments) {}
