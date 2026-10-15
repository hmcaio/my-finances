package com.chm.myfinances.infrastructure.web.auth;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/login} body (F036, ADR 0029). Bounded like every other free-text input; the
 * generated {@code toString()} is overridden because it would otherwise print the password.
 */
public record LoginRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String username,
    @NotBlank @Size(max = LoginRequest.MAX_PASSWORD_LENGTH) String password) {

  /** Generous for any real passphrase; stops an absurd body from being hashed. */
  static final int MAX_PASSWORD_LENGTH = 256;

  @Override
  public String toString() {
    return "LoginRequest[redacted]";
  }
}
