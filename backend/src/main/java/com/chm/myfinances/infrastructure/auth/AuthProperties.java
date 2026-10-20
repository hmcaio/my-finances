package com.chm.myfinances.infrastructure.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The single shared credential and the login rate-limit knobs (F036, ADR 0029), bound from {@code
 * myfinances.auth.*}. {@code username}/{@code passwordHash} come from the {@code AUTH_USERNAME} /
 * {@code AUTH_PASSWORD_HASH} environment variables (see {@code application.yml}) and are validated
 * at startup, so a missing or mangled credential fails the boot with a message that says what to
 * fix instead of silently leaving the app locked (or, worse, open).
 *
 * <p>Never log this record or either credential field (ADR 0011): the generated {@code toString()}
 * is overridden for that reason.
 *
 * @param username the one accepted username
 * @param passwordHash the Argon2 encoded hash (never the plaintext) from {@code ./gradlew
 *     generateAuthHash}
 * @param maxFailedAttempts failed logins from one client within {@code failureWindow} that start a
 *     cooldown
 * @param failureWindow how long a failure counts toward the threshold
 * @param lockoutDuration how long further attempts are rejected with {@code 429} once tripped
 * @param clientIpHeader optional request header carrying the real client address when the backend
 *     sits behind a reverse proxy that sets it (prod: nginx's {@code X-Real-IP}); blank means use
 *     the socket's remote address. Only safe to set where the backend is reachable through that
 *     proxy alone.
 */
@Validated
@ConfigurationProperties("myfinances.auth")
public record AuthProperties(
    @NotBlank(message = "AUTH_USERNAME must be set") String username,
    @NotBlank(
            message =
                "AUTH_PASSWORD_HASH must be set - generate one with ./gradlew generateAuthHash")
        @Pattern(
            regexp = "^\\$argon2(id|i|d)\\$.+",
            message =
                "AUTH_PASSWORD_HASH must be an Argon2 hash from ./gradlew generateAuthHash, not a"
                    + " plaintext password; in a .env file wrap it in single quotes so Docker"
                    + " Compose does not treat its $ characters as variable references")
        String passwordHash,
    @DefaultValue("5") @Min(1) int maxFailedAttempts,
    @DefaultValue("15m") @NotNull Duration failureWindow,
    @DefaultValue("15m") @NotNull Duration lockoutDuration,
    String clientIpHeader) {

  @Override
  public String toString() {
    return "AuthProperties[redacted]";
  }
}
