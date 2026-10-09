package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.application.auditlog.AuditEntry;
import com.chm.myfinances.application.auditlog.AuditLog;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link AuditLog}, shared across application-service tests (mirrors every other
 * {@code Fake*Repository} in this package). Just records every entry it's given, in order, so a
 * test can assert on exactly what a write use case emitted without a Spring context or a real
 * database.
 */
public final class FakeAuditLog implements AuditLog {

  private final List<AuditEntry> entries = new ArrayList<>();

  @Override
  public void record(AuditEntry entry) {
    entries.add(entry);
  }

  public List<AuditEntry> entries() {
    return entries;
  }

  public AuditEntry onlyEntry() {
    if (entries.size() != 1) {
      throw new AssertionError("expected exactly one audit entry, got " + entries.size());
    }
    return entries.get(0);
  }
}
