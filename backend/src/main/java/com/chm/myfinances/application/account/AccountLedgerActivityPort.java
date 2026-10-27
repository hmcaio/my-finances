package com.chm.myfinances.application.account;

import com.chm.myfinances.domain.account.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-model port (ADR 0004) for the net effect of the ledger on account balances: for each given
 * non-{@code INVESTMENT} account, the signed sum of every transaction and transfer posted to it on
 * or before a date, the signs being those of {@link AccountBalanceQuery#signedContribution} and
 * {@link AccountBalanceQuery#transferContribution} (PRD S5.3, S5.5). The opening balance is not
 * included; {@link AccountBalanceQuery} adds it. Exists so a balance read costs a constant number
 * of statements instead of loading every ledger row into the JVM (issue #189).
 *
 * <p>The adapter restates the sign rules in SQL, so a parity test ({@code
 * AccountLedgerActivityParityTest}) runs it against the Java rules on randomized data.
 */
public interface AccountLedgerActivityPort {

  /**
   * One entry per given account, {@code 0} when it has no ledger rows by {@code asOfDate}. Callers
   * never pass an {@code INVESTMENT} account (it has no ledger balance, ADR 0012).
   */
  Map<UUID, BigDecimal> activityAsOf(Collection<Account> accounts, LocalDate asOfDate);
}
