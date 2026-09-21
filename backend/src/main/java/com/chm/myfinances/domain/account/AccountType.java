package com.chm.myfinances.domain.account;

/**
 * Account type (PRD S5.4). Drives whether the running balance is an asset or a liability.
 *
 * <p>{@code INVESTMENT} (F008, ADR 0012) is an ordinary account for grouping and closing, but has
 * no opening balance/date and takes no transactions or recurring templates - its value comes only
 * from its products' snapshots (F009) and money moves in and out through transfers.
 */
public enum AccountType {
  CHECKING,
  SAVINGS,
  CASH_WALLET,
  CREDIT_CARD,
  INVESTMENT
}
