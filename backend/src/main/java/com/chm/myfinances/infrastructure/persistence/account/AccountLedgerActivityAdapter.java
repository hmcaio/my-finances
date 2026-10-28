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
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link AccountLedgerActivityPort} in one native SQL statement per call (issue #189). The sign
 * rules are those of {@code AccountBalanceQuery.signedContribution}/{@code transferContribution},
 * restated here (PRD S5.3, S5.5):
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

  /**
   * Every ledger row up to {@code :upTo} that touches one of {@code :ids}, as a signed amount on
   * that account: the one place the sign rules live in SQL. A transfer is two rows, one per side.
   */
  private static final String EVENTS_CTE =
      """
      with events as (
        select t.account_id as account_id, t.date as date,
               case when (t.type = 'EXPENSE') = (a.type = 'CREDIT_CARD')
                    then t.amount else -t.amount end as amount
          from transactions t
          join accounts a on a.id = t.account_id
         where t.account_id in (:ids) and t.date <= :upTo
        union all
        select x.from_account_id, x.date, -x.amount
          from transfers x
         where x.from_account_id in (:ids) and x.date <= :upTo
        union all
        select x.to_account_id, x.date,
               case when a.type = 'CREDIT_CARD' then -x.amount else x.amount end
          from transfers x
          join accounts a on a.id = x.to_account_id
         where x.to_account_id in (:ids) and x.date <= :upTo
      )
      """;

  private static final String ACTIVITY_SQL =
      EVENTS_CTE
          + """
      select a.id, coalesce(sum(e.amount), 0)
        from accounts a
        left join events e on e.account_id = a.id
       where a.id in (:ids)
       group by a.id
      """;

  private static final String DAILY_ACTIVITY_SQL =
      EVENTS_CTE
          + """
      select account_id, date, sum(amount)
        from events
       group by account_id, date
      """;

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Map<UUID, BigDecimal> activityAsOf(Collection<Account> accounts, LocalDate asOfDate) {
    Map<UUID, BigDecimal> activity = new HashMap<>();
    for (Object[] row : rows(ACTIVITY_SQL, accounts, asOfDate)) {
      activity.put((UUID) row[0], (BigDecimal) row[1]);
    }
    return activity;
  }

  @Override
  public Map<UUID, NavigableMap<LocalDate, BigDecimal>> dailyActivityUpTo(
      Collection<Account> accounts, LocalDate upTo) {
    Map<UUID, NavigableMap<LocalDate, BigDecimal>> daily = new HashMap<>();
    for (Account account : accounts) {
      daily.put(account.getId(), new TreeMap<>());
    }
    for (Object[] row : rows(DAILY_ACTIVITY_SQL, accounts, upTo)) {
      daily.get((UUID) row[0]).put(toLocalDate(row[1]), (BigDecimal) row[2]);
    }
    return daily;
  }

  private List<Object[]> rows(String sql, Collection<Account> accounts, LocalDate upTo) {
    if (accounts.isEmpty()) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(sql)
            .setParameter("upTo", upTo)
            .setParameter("ids", accounts.stream().map(Account::getId).toList())
            .getResultList();
    return rows;
  }

  private static LocalDate toLocalDate(Object value) {
    return value instanceof LocalDate date ? date : ((java.sql.Date) value).toLocalDate();
  }
}
