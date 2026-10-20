package com.chm.myfinances.infrastructure.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-client failed-login tracker behind the {@code 429 too-many-login-attempts}
 * response (F036, ADR 0029): the overlay network is the primary gate, this is the second one
 * against a compromised device on the same network hammering {@code POST /api/auth/login}.
 *
 * <p>{@code maxFailures} failures from one client within {@code failureWindow} start a cooldown of
 * {@code lockout}; during it the login endpoint rejects the client without evaluating the
 * credential at all (so even the right password is refused). Once it elapses the client starts from
 * zero. A successful login clears the client's record. Nothing is persisted - a backend restart
 * clears the tracker, like the session store. Time comes only from the injected {@link Clock}.
 *
 * <p>Keys are client addresses, never usernames: nothing here ever holds or logs a credential.
 */
@Component
public class LoginAttemptTracker {

  /** Opportunistic cleanup threshold - a personal app never gets near it. */
  private static final int PRUNE_ABOVE_ENTRIES = 1_000;

  private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {}

  private final Clock clock;
  private final int maxFailures;
  private final Duration failureWindow;
  private final Duration lockout;
  private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

  @Autowired
  public LoginAttemptTracker(Clock clock, AuthProperties properties) {
    this(
        clock,
        properties.maxFailedAttempts(),
        properties.failureWindow(),
        properties.lockoutDuration());
  }

  LoginAttemptTracker(Clock clock, int maxFailures, Duration failureWindow, Duration lockout) {
    this.clock = clock;
    this.maxFailures = maxFailures;
    this.failureWindow = failureWindow;
    this.lockout = lockout;
  }

  /** True while {@code client} is inside a cooldown. */
  public boolean isLocked(String client) {
    Attempts current = attempts.get(client);
    if (current == null || current.lockedUntil() == null) {
      return false;
    }
    if (clock.instant().isBefore(current.lockedUntil())) {
      return true;
    }
    attempts.remove(client, current);
    return false;
  }

  /**
   * Records one failed login and returns how many consecutive failures {@code client} now has
   * inside the current window (the count a log line may carry).
   */
  public int recordFailure(String client) {
    Instant now = clock.instant();
    if (attempts.size() > PRUNE_ABOVE_ENTRIES) {
      attempts.values().removeIf(a -> isStale(a, now));
    }
    return attempts
        .compute(
            client,
            (key, previous) -> {
              int failures = isStale(previous, now) ? 1 : previous.failures() + 1;
              Instant windowStart = isStale(previous, now) ? now : previous.windowStart();
              Instant lockedUntil = failures >= maxFailures ? now.plus(lockout) : null;
              return new Attempts(failures, windowStart, lockedUntil);
            })
        .failures();
  }

  /** A successful login forgets the client's failures. */
  public void recordSuccess(String client) {
    attempts.remove(client);
  }

  /** No record, a finished cooldown, or a window that has run out: counting starts over. */
  private boolean isStale(Attempts record, Instant now) {
    if (record == null) {
      return true;
    }
    if (record.lockedUntil() != null) {
      return !now.isBefore(record.lockedUntil());
    }
    return now.isAfter(record.windowStart().plus(failureWindow));
  }
}
