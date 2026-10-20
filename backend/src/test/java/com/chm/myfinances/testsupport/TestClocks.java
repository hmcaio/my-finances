package com.chm.myfinances.testsupport;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fixed {@link Clock}s in the JVM default zone, the zone production reads "today" in ({@code
 * Clock.systemDefaultZone()}). Tests run under a pinned non-UTC zone (build.gradle), so a
 * UTC-vs-local bug in the code under test fails here instead of only on a developer's machine in
 * the evening; a UTC clock in a test would hide exactly that.
 */
public final class TestClocks {

  private TestClocks() {}

  /** A clock fixed at midnight at the start of {@code date}, in the default zone. */
  public static Clock fixedAtStartOf(LocalDate date) {
    ZoneId zone = ZoneId.systemDefault();
    return Clock.fixed(date.atStartOfDay(zone).toInstant(), zone);
  }
}
