package com.chm.myfinances.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * F036: the one configured user authenticates against the configured Argon2 hash through the same
 * {@code DaoAuthenticationProvider} wiring {@code SecurityConfig} uses; a wrong password and a
 * wrong username fail identically, so the response never says which field was wrong.
 */
class SingleUserDetailsServiceTest {

  private static final String USERNAME = "owner";
  private static final String PASSWORD = "correct horse battery staple";

  private static AuthenticationManager manager;

  @BeforeAll
  static void wire() {
    Argon2PasswordEncoder encoder = AuthPasswordEncoder.create();
    SingleUserDetailsService users =
        new SingleUserDetailsService(USERNAME, encoder.encode(PASSWORD));
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    manager = new ProviderManager(provider);
  }

  private static Authentication attempt(String username, String password) {
    return manager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(username, password));
  }

  @Test
  void configuredCredentialsAuthenticate() {
    Authentication result = attempt(USERNAME, PASSWORD);

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getName()).isEqualTo(USERNAME);
  }

  @Test
  void wrongPasswordFails() {
    assertThatThrownBy(() -> attempt(USERNAME, "not the password"))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void wrongUsernameFailsWithTheSameExceptionAsAWrongPassword() {
    Throwable wrongPassword = catchThrowable(() -> attempt(USERNAME, "not the password"));
    Throwable wrongUsername = catchThrowable(() -> attempt("someone-else", PASSWORD));

    assertThat(wrongUsername).isInstanceOf(BadCredentialsException.class);
    assertThat(wrongUsername.getClass()).isEqualTo(wrongPassword.getClass());
    assertThat(wrongUsername.getMessage()).isEqualTo(wrongPassword.getMessage());
  }

  @Test
  void usernameComparisonIsCaseSensitive() {
    assertThatThrownBy(() -> attempt("OWNER", PASSWORD))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void generatedHashIsArgon2AndNeverTheEncoderOfAnotherAlgorithm() {
    String hash = AuthPasswordEncoder.create().encode(PASSWORD);

    assertThat(hash).startsWith("$argon2id$");
    assertThat(hash).doesNotContain(PASSWORD);
  }

  private static Throwable catchThrowable(Runnable runnable) {
    try {
      runnable.run();
    } catch (RuntimeException e) {
      return e;
    }
    throw new AssertionError("expected an exception");
  }
}
