package com.chm.myfinances.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * F036: N failures from one client within the window trip a cooldown, the cooldown ends by itself,
 * and clients are tracked independently. Pure unit test - the tracker reads time only through the
 * injected {@code Clock}.
 */
class LoginAttemptTrackerTest {

  private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");

  private final MutableClock clock = new MutableClock();
  private LoginAttemptTracker tracker;

  @BeforeEach
  void setUp() {
    clock.set(T0, ZoneOffset.UTC);
    tracker = new LoginAttemptTracker(clock, 3, Duration.ofMinutes(15), Duration.ofMinutes(10));
  }

  private void advance(Duration duration) {
    clock.set(clock.instant().plus(duration), ZoneOffset.UTC);
  }

  @Test
  void aFreshClientIsNotLocked() {
    assertThat(tracker.isLocked("10.0.0.1")).isFalse();
  }

  @Test
  void failuresBelowTheThresholdDoNotLock() {
    tracker.recordFailure("10.0.0.1");
    tracker.recordFailure("10.0.0.1");

    assertThat(tracker.isLocked("10.0.0.1")).isFalse();
  }

  @Test
  void reachingTheThresholdLocksTheClient() {
    tracker.recordFailure("10.0.0.1");
    tracker.recordFailure("10.0.0.1");
    int count = tracker.recordFailure("10.0.0.1");

    assertThat(count).isEqualTo(3);
    assertThat(tracker.isLocked("10.0.0.1")).isTrue();
  }

  @Test
  void theLockoutEndsAfterTheLockoutDuration() {
    for (int i = 0; i < 3; i++) {
      tracker.recordFailure("10.0.0.1");
    }
    advance(Duration.ofMinutes(9));
    assertThat(tracker.isLocked("10.0.0.1")).isTrue();

    advance(Duration.ofMinutes(2));

    assertThat(tracker.isLocked("10.0.0.1")).isFalse();
  }

  @Test
  void afterTheLockoutTheClientStartsFromZeroAgain() {
    for (int i = 0; i < 3; i++) {
      tracker.recordFailure("10.0.0.1");
    }
    advance(Duration.ofMinutes(11));

    tracker.recordFailure("10.0.0.1");
    tracker.recordFailure("10.0.0.1");

    assertThat(tracker.isLocked("10.0.0.1")).isFalse();
  }

  @Test
  void failuresOutsideTheWindowDoNotAccumulate() {
    tracker.recordFailure("10.0.0.1");
    tracker.recordFailure("10.0.0.1");
    advance(Duration.ofMinutes(16));

    tracker.recordFailure("10.0.0.1");

    assertThat(tracker.isLocked("10.0.0.1")).isFalse();
  }

  @Test
  void aSuccessfulLoginClearsTheFailureCount() {
    tracker.recordFailure("10.0.0.1");
    tracker.recordFailure("10.0.0.1");
    tracker.recordSuccess("10.0.0.1");

    tracker.recordFailure("10.0.0.1");
    tracker.recordFailure("10.0.0.1");

    assertThat(tracker.isLocked("10.0.0.1")).isFalse();
  }

  @Test
  void clientsAreTrackedIndependently() {
    for (int i = 0; i < 3; i++) {
      tracker.recordFailure("10.0.0.1");
    }

    assertThat(tracker.isLocked("10.0.0.1")).isTrue();
    assertThat(tracker.isLocked("10.0.0.2")).isFalse();
  }
}
