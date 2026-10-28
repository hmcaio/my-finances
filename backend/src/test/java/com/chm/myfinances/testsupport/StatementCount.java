package com.chm.myfinances.testsupport;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Counts the SQL statements a read sends to the database, to pin that it does not grow with the
 * number of rows (the N+1 of issue #189: one statement per transfer for its trade lines). Built on
 * Hibernate's statistics, so it sees every prepared statement of the {@link EntityManager}'s
 * session factory: derived and JPQL queries, native queries and lazy loads alike. Pair it with a
 * second measurement on more data and assert the two counts are equal, since a fixed cap alone
 * passes a read that fetches a few children per element.
 */
public final class StatementCount {

  private StatementCount() {}

  /** The statements {@code read} prepared. Flush and clear the persistence context before it. */
  public static long of(EntityManager entityManager, Runnable read) {
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    read.run();
    return statistics.getPrepareStatementCount();
  }
}
