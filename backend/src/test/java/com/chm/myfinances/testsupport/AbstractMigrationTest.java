package com.chm.myfinances.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Shared harness for "testing a migration against pre-existing data" (backend {@code CLAUDE.md}):
 * run Flyway by hand into a throwaway schema of the shared Testcontainers Postgres, insert rows
 * that predate the migration under test, migrate one version further, then assert. Extracted from
 * four near-identical classes (issue #31, B8): {@code BuiltInCategoriesMigrationTest}, {@code
 * InstitutionBackfillMigrationTest}, {@code InvestmentTaxonomyMigrationTest}, {@code
 * PendingOccurrenceCycleUniquenessMigrationTest}.
 *
 * <p>Each subclass still declares its own {@code @SpringBootTest @Import(
 * TestcontainersConfiguration.class)} (deliberately not the {@link DatabaseIntegrationTest}
 * meta-annotation: a migration test must not roll back like an ordinary DB test — it drops its
 * whole throwaway schema instead) and passes its own distinct schema name to the constructor here,
 * so the four classes never collide when Gradle runs them against the same container.
 */
public abstract class AbstractMigrationTest {

  private final String schema;

  @Autowired protected DataSource pooledDataSource;

  /**
   * Non-pooled, on purpose: {@code Connection.setSchema} changes the physical connection's {@code
   * search_path}, and on a pooled connection that outlives this test - the next test to borrow it
   * then fails with "relation does not exist" once the schema is dropped.
   */
  protected DataSource dataSource;

  protected AbstractMigrationTest(String schema) {
    this.schema = schema;
  }

  @BeforeEach
  void useAnIsolatedDataSource() throws SQLException {
    HikariDataSource pool = pooledDataSource.unwrap(HikariDataSource.class);
    dataSource =
        new DriverManagerDataSource(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
  }

  @AfterEach
  void dropSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  protected Flyway flyway(String targetVersion) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .target(targetVersion)
        .load();
  }

  /** A connection whose default schema is the throwaway one Flyway just migrated. */
  protected Connection connection() throws SQLException {
    Connection connection = dataSource.getConnection();
    connection.setSchema(schema);
    return connection;
  }

  protected static long count(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  /** The single-column string results of {@code sql} (e.g. a list of names). */
  protected static Set<String> namesFrom(Connection connection, String sql) throws SQLException {
    Set<String> names = new HashSet<>();
    try (PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        names.add(rows.getString(1));
      }
    }
    return names;
  }

  protected boolean isNullable(Connection connection, String table, String column)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
      statement.setString(1, schema);
      statement.setString(2, table);
      statement.setString(3, column);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("column %s.%s exists", table, column).isTrue();
        return "YES".equals(rows.getString(1));
      }
    }
  }
}
