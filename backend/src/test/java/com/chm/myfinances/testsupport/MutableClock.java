package com.chm.myfinances.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} that delegates to {@link Clock#systemDefaultZone()} until {@link #set} pins it to
 * a fixed instant, and back again on {@link #reset} - lets a single {@code @SpringBootTest} method
 * fix "today" for the app's one {@code Clock} bean (see {@code infrastructure/config/ClockConfig})
 * without affecting every other test that shares the same cached Spring context (issue #31, B1).
 * Register it as a {@code @Primary} bean in a {@code @TestConfiguration} to override the real
 * {@code Clock} bean, reset it in an {@code @AfterEach} so the fix never leaks into another test.
 */
public final class MutableClock extends Clock {

  private volatile Clock delegate = Clock.systemDefaultZone();

  /** Pins this clock to {@code instant} in {@code zone} until {@link #reset()}. */
  public void set(Instant instant, ZoneId zone) {
    delegate = Clock.fixed(instant, zone);
  }

  /** Restores the real system clock. */
  public void reset() {
    delegate = Clock.systemDefaultZone();
  }

  @Override
  public ZoneId getZone() {
    return delegate.getZone();
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return delegate.withZone(zone);
  }

  @Override
  public Instant instant() {
    return delegate.instant();
  }
}
