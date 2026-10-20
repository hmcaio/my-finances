package com.chm.myfinances.infrastructure.auth;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * One-off setup tool behind {@code ./gradlew generateAuthHash} (F036, ADR 0029; mirrors F018's
 * {@code backup-keygen} as setup-time tooling): turns a plaintext password into the Argon2 hash to
 * put in {@code AUTH_PASSWORD_HASH}. Deliberately a command-line entry point, never an HTTP
 * endpoint - hashing is a once-per-credential operation and a runtime endpoint would be needless
 * attack surface.
 *
 * <p>The password is read from the {@code AUTH_PASSWORD} environment variable if set, otherwise
 * prompted for on stdin (silently when a console is attached, which Gradle's daemon is not - there
 * the typed text echoes). Neither path leaves the plaintext in shell history. The prompt and notes
 * go to stderr; stdout carries only the ready-to-paste {@code AUTH_PASSWORD_HASH='...'} line.
 */
public final class AuthHashGenerator {

  /** Shorter than this still works, but earns a warning on stderr. */
  static final int RECOMMENDED_MIN_LENGTH = 12;

  private AuthHashGenerator() {}

  public static void main(String[] args) throws IOException {
    String password = System.getenv("AUTH_PASSWORD");
    if (password == null || password.isEmpty()) {
      password = promptForPassword();
    }
    if (password == null || password.isBlank()) {
      System.err.println("No password given; nothing generated.");
      System.exit(1);
    }
    if (password.length() < RECOMMENDED_MIN_LENGTH) {
      System.err.println(
          "Warning: that password is shorter than " + RECOMMENDED_MIN_LENGTH + " characters.");
    }
    String hash = AuthPasswordEncoder.create().encode(password);
    System.err.println(
        "Put this line in .env next to AUTH_USERNAME. The single quotes are required: the hash"
            + " contains $ characters that Docker Compose would otherwise treat as variable"
            + " references.");
    System.out.println("AUTH_PASSWORD_HASH='" + hash + "'");
  }

  private static String promptForPassword() throws IOException {
    System.err.print("Password to hash (or set AUTH_PASSWORD to skip this prompt): ");
    System.err.flush();
    Console console = System.console();
    if (console != null) {
      char[] chars = console.readPassword();
      return chars == null ? null : new String(chars);
    }
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    return reader.readLine();
  }
}
