package com.chm.myfinances.infrastructure.persistence.account;

import com.chm.myfinances.application.account.AccountLedgerActivityPort;
import com.chm.myfinances.domain.account.Account;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link AccountLedgerActivityPort} in one native SQL statement (issue #189). The sign rules are
 * those of {@code AccountBalanceQuery.signedContribution}/{@code transferContribution}, restated
 * here (PRD S5.3, S5.5):
 *
 * <ul>
 *   <li>a transaction adds its amount when it is an expense on a credit card or an income on any
 *       other account, and subtracts it otherwise;
 *   <li>a transfer subtracts its amount from its source account, whatever the type, and from its
 *       destination when that is a credit card, and adds it to any other destination.
 * </ul>
 *
 * {@code AccountLedgerActivityParityTest} keeps the two in step. It goes through the {@link
 * EntityManager}, not a {@code JdbcTemplate}, so Hibernate flushes pending writes of the current
 * transaction before the query runs.
 */
@Component
public class AccountLedgerActivityAdapter implements AccountLedgerActivityPort {

  private static final String ACTIVITY_SQL =
      """
      select a.id,
             coalesce((select sum(case when (t.type = 'EXPENSE') = (a.type = 'CREDIT_CARD')
                                       then t.amount else -t.amount end)
                         from transactions t
                        where t.account_id = a.id and t.date <= :asOf), 0)
           + coalesce((select sum(case when x.from_account_id = a.id then -x.amount
                                       when a.type = 'CREDIT_CARD' then -x.amount
                                       else x.amount end)
                         from transfers x
                        where (x.from_account_id = a.id or x.to_account_id = a.id)
                          and x.date <= :asOf), 0) as activity
        from accounts a
       where a.id in (:ids)
      """;

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Map<UUID, BigDecimal> activityAsOf(Collection<Account> accounts, LocalDate asOfDate) {
    if (accounts.isEmpty()) {
      return Map.of();
    }
    List<?> rows =
        entityManager
            .createNativeQuery(ACTIVITY_SQL)
            .setParameter("asOf", asOfDate)
            .setParameter("ids", accounts.stream().map(Account::getId).toList())
            .getResultList();
    Map<UUID, BigDecimal> activity = new HashMap<>();
    for (Object row : rows) {
      Object[] columns = (Object[]) row;
      activity.put((UUID) columns[0], (BigDecimal) columns[1]);
    }
    return activity;
  }
}
