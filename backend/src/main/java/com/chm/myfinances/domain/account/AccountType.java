package com.chm.myfinances.domain.account;

/** Account type (PRD S5.4). Drives whether the running balance is an asset or a liability. */
public enum AccountType {
  CHECKING,
  SAVINGS,
  CASH_WALLET,
  CREDIT_CARD
}
