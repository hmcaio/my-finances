package com.chm.myfinances.infrastructure.persistence.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Proves {@code V25__enable_unaccent.sql} (F037, ADR 0030): on the exact pinned {@code
 * postgres:17.11-alpine} image the extension is available, {@code CREATE EXTENSION IF NOT EXISTS
 * unaccent} succeeds inside Flyway's per-migration transaction on a database that already holds the
 * earlier migrations, and {@code unaccent} then folds accents.
 *
 * <p>The extension is database-wide, and the shared test database already has it (the Spring
 * context migrated it), so a throwaway schema (the {@code AbstractMigrationTest} pattern) would
 * prove nothing. This test therefore migrates a throwaway <em>database</em> of the same container:
 * V1..V24 first, then V25 on its own, with a non-pooled connection (a pooled one would leak the
 * database into other tests).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UnaccentMigrationTest {

  private static final String DATABASE = "migration_test_unaccent";

  @Autowired private DataSource pooledDataSource;

  private HikariDataSource pool;
  private DataSource throwawayDataSource;

  @BeforeEach
  void createThrowawayDatabase() throws SQLException {
    pool = pooledDataSource.unwrap(HikariDataSource.class);
    execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    execute("CREATE DATABASE " + DATABASE);
    String url = pool.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1");
    throwawayDataSource = new DriverManagerDataSource(url, pool.getUsername(), pool.getPassword());
  }

  @AfterEach
  void dropThrowawayDatabase() throws SQLException {
    execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
  }

  private void execute(String sql) throws SQLException {
    try (Connection connection =
            new DriverManagerDataSource(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())
                .getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private Flyway flyway(String targetVersion) {
    return Flyway.configure()
        .dataSource(throwawayDataSource)
        .locations("classpath:db/migration")
        .target(targetVersion)
        .load();
  }

  private String queryString(String sql) throws SQLException {
    try (Connection connection = throwawayDataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getString(1);
    }
  }

  @Test
  void theExtensionIsNotThereBeforeV25() throws SQLException {
    flyway("24").migrate();

    assertThat(queryString("SELECT count(*) FROM pg_extension WHERE extname = 'unaccent'"))
        .isEqualTo("0");
  }

  @Test
  void v25EnablesUnaccentOnADatabaseThatAlreadyHoldsTheEarlierMigrations() throws SQLException {
    flyway("24").migrate();

    flyway("25").migrate();

    assertThat(queryString("SELECT count(*) FROM pg_extension WHERE extname = 'unaccent'"))
        .isEqualTo("1");
    assertThat(queryString("SELECT unaccent('Café açaí')")).isEqualTo("Cafe acai");
    assertThat(queryString("SELECT success FROM flyway_schema_history WHERE version = '25'"))
        .isEqualTo("t");
  }

  @Test
  void unaccentLeavesTheLikeEscapeCharactersUnchanged() throws SQLException {
    flyway("25").migrate();

    // F037 open question, confirmed: applying unaccent to the already-escaped LIKE pattern keeps
    // the backslash, percent and underscore intact, so the escapes still work.
    assertThat(queryString("SELECT unaccent(E'%caf\\\\%é\\\\_x\\\\\\\\%')"))
        .isEqualTo("%caf\\%e\\_x\\\\%");
  }
}
