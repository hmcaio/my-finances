package com.chm.myfinances.infrastructure.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * The one place the app's Argon2 parameters are chosen (F036, ADR 0029). Both the runtime {@code
 * PasswordEncoder} bean and the one-off {@link AuthHashGenerator} ({@code ./gradlew
 * generateAuthHash}) build their encoder here, so a generated hash is always verifiable by the
 * running app. {@code matches} reads the parameters out of the stored hash itself, so changing the
 * defaults later never invalidates an existing {@code AUTH_PASSWORD_HASH}.
 */
public final class AuthPasswordEncoder {

  private AuthPasswordEncoder() {}

  public static Argon2PasswordEncoder create() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }
}
