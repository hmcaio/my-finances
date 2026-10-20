package com.chm.myfinances.infrastructure.persistence.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditEntry;
import com.chm.myfinances.application.auditlog.AuditLog;
import com.chm.myfinances.application.auditlog.AuditLogFilter;
import com.chm.myfinances.application.auditlog.AuditLogRecord;
import com.chm.myfinances.application.auditlog.AuditLogRepository;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.FieldChange;
import com.chm.myfinances.infrastructure.web.RequestLoggingFilter;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Persistence-layer integration test for {@link AuditLogRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010), so Flyway's {@code V23__audit_log.sql} runs for real too.
 * Covers the {@code jsonb} round trip, {@code occurredAt} coming from the injected {@code Clock}
 * (never SQL {@code now()}), {@code requestId} being read from the MDC rather than the caller, and
 * the read side's filters/pagination.
 */
@DatabaseIntegrationTest
@Import(AuditLogRepositoryAdapterTest.FixedClockTestConfig.class)
class AuditLogRepositoryAdapterTest {

  @TestConfiguration(proxyBeanMethods = false)
  public static class FixedClockTestConfig {
    @Bean
    @Primary
    public MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired private AuditLog auditLog;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private MutableClock testClock;

  @BeforeEach
  void setUp() {
    testClock.set(
        LocalDate.of(2026, 3, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());
  }

  @AfterEach
  void tearDown() {
    MDC.remove(RequestLoggingFilter.MDC_KEY);
    testClock.reset();
  }

  @Test
  void recordThenFindAllRoundTripsTheJsonbChanges() {
    UUID entityId = UUID.randomUUID();
    Map<String, FieldChange> changes =
        Map.of(
            "name",
            new FieldChange("Old Name", "New Name"),
            "amount",
            new FieldChange(null, "42.50"));
    auditLog.record(
        new AuditEntry(
            AuditEntityType.ACCOUNT,
            entityId,
            "New Name",
            AuditAction.UPDATE,
            AuditOrigin.USER,
            changes,
            null));

    Page<AuditLogRecord> page =
        auditLogRepository.findAll(
            new AuditLogFilter(null, null, null, null, null, entityId), PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    AuditLogRecord record = page.getContent().get(0);
    assertThat(record.entityType()).isEqualTo(AuditEntityType.ACCOUNT);
    assertThat(record.entityId()).isEqualTo(entityId);
    assertThat(record.entityLabel()).isEqualTo("New Name");
    assertThat(record.action()).isEqualTo(AuditAction.UPDATE);
    assertThat(record.origin()).isEqualTo(AuditOrigin.USER);
    assertThat(record.changes()).isEqualTo(changes);
  }

  @Test
  void occurredAtComesFromTheInjectedClockNotRealTime() {
    UUID entityId = UUID.randomUUID();
    Instant fixedInstant =
        LocalDate.of(2026, 3, 15).atStartOfDay(ZoneId.systemDefault()).toInstant();

    auditLog.record(
        new AuditEntry(
            AuditEntityType.CATEGORY,
            entityId,
            "Groceries",
            AuditAction.CREATE,
            AuditOrigin.USER,
            Map.of("name", new FieldChange(null, "Groceries")),
            null));

    AuditLogRecord record =
        auditLogRepository
            .findAll(
                new AuditLogFilter(null, null, null, null, null, entityId), PageRequest.of(0, 10))
            .getContent()
            .get(0);
    assertThat(record.occurredAt()).isEqualTo(fixedInstant);
  }

  @Test
  void requestIdIsReadFromTheMdcNotTheCaller() {
    UUID entityId = UUID.randomUUID();
    MDC.put(RequestLoggingFilter.MDC_KEY, "test-request-id-123");

    auditLog.record(
        new AuditEntry(
            AuditEntityType.CATEGORY,
            entityId,
            "Groceries",
            AuditAction.CREATE,
            AuditOrigin.USER,
            Map.of("name", new FieldChange(null, "Groceries")),
            null));

    AuditLogRecord record =
        auditLogRepository
            .findAll(
                new AuditLogFilter(null, null, null, null, null, entityId), PageRequest.of(0, 10))
            .getContent()
            .get(0);
    assertThat(record.requestId()).isEqualTo("test-request-id-123");
  }

  @Test
  void findAllFiltersByEntityTypeActionAndOrigin() {
    UUID accountId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    auditLog.record(
        new AuditEntry(
            AuditEntityType.ACCOUNT,
            accountId,
            "Nubank",
            AuditAction.CLOSE,
            AuditOrigin.USER,
            Map.of("closedDate", new FieldChange(null, "2026-03-15")),
            null));
    auditLog.record(
        new AuditEntry(
            AuditEntityType.CATEGORY,
            categoryId,
            "Groceries",
            AuditAction.CREATE,
            AuditOrigin.SYSTEM,
            Map.of("name", new FieldChange(null, "Groceries")),
            null));

    Page<AuditLogRecord> accountEntries =
        auditLogRepository.findAll(
            new AuditLogFilter(null, null, AuditEntityType.ACCOUNT, null, null, null),
            PageRequest.of(0, 10));
    assertThat(accountEntries.getContent())
        .extracting(AuditLogRecord::entityId)
        .containsExactly(accountId);

    Page<AuditLogRecord> systemEntries =
        auditLogRepository.findAll(
            new AuditLogFilter(null, null, null, null, AuditOrigin.SYSTEM, null),
            PageRequest.of(0, 10));
    assertThat(systemEntries.getContent())
        .extracting(AuditLogRecord::entityId)
        .containsExactly(categoryId);
  }

  @Test
  void findAllOrdersNewestFirst() {
    UUID entityId = UUID.randomUUID();
    testClock.set(
        LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());
    auditLog.record(
        new AuditEntry(
            AuditEntityType.CATEGORY,
            entityId,
            "First",
            AuditAction.CREATE,
            AuditOrigin.USER,
            Map.of("name", new FieldChange(null, "First")),
            null));
    testClock.set(
        LocalDate.of(2026, 6, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());
    auditLog.record(
        new AuditEntry(
            AuditEntityType.CATEGORY,
            entityId,
            "Second",
            AuditAction.UPDATE,
            AuditOrigin.USER,
            Map.of("name", new FieldChange("First", "Second")),
            null));

    List<AuditLogRecord> entries =
        auditLogRepository
            .findAll(
                new AuditLogFilter(null, null, null, null, null, entityId),
                PageRequest.of(
                    0,
                    10,
                    org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "occurredAt")))
            .getContent();

    assertThat(entries).extracting(AuditLogRecord::entityLabel).containsExactly("Second", "First");
  }
}
