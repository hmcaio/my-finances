package com.chm.myfinances.infrastructure.web.auditlog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditEntry;
import com.chm.myfinances.application.auditlog.AuditLog;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.FieldChange;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link AuditLogController}, against a real Testcontainers
 * Postgres (ADR 0010). Hand-built {@link MockMvc} - same pattern as F002's {@code
 * CategoryControllerTest}. Read-only: there is nothing to {@code POST}/{@code PATCH}/{@code DELETE}
 * here (F025 spec's API section), so entries are seeded directly through the {@link AuditLog} port
 * rather than via another endpoint's side effect.
 */
@WebIntegrationTest
class AuditLogControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AuditLog auditLog;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void listReturnsAPagedModelOfEveryEntry() throws Exception {
    UUID entityId = UUID.randomUUID();
    auditLog.record(
        new AuditEntry(
            AuditEntityType.CATEGORY,
            entityId,
            "Groceries",
            AuditAction.CREATE,
            AuditOrigin.USER,
            Map.of("name", new FieldChange(null, "Groceries")),
            null));

    mockMvc
        .perform(get("/api/audit-log").param("entityId", entityId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].entityType").value("CATEGORY"))
        .andExpect(jsonPath("$.content[0].entityLabel").value("Groceries"))
        .andExpect(jsonPath("$.content[0].action").value("CREATE"))
        .andExpect(jsonPath("$.content[0].origin").value("USER"))
        .andExpect(jsonPath("$.content[0].changes.name.to").value("Groceries"))
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void listFiltersByAction() throws Exception {
    UUID accountId = UUID.randomUUID();
    auditLog.record(
        new AuditEntry(
            AuditEntityType.ACCOUNT,
            accountId,
            "Nubank",
            AuditAction.CLOSE,
            AuditOrigin.USER,
            Map.of("closedDate", new FieldChange(null, "2026-03-15")),
            null));

    mockMvc
        .perform(
            get("/api/audit-log").param("entityId", accountId.toString()).param("action", "CLOSE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/audit-log").param("entityId", accountId.toString()).param("action", "DELETE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void listPagesResults() throws Exception {
    mockMvc
        .perform(get("/api/audit-log").param("page", "0").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(5))
        .andExpect(jsonPath("$.page.number").value(0));
  }
}
