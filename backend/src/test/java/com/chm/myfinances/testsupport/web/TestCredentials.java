package com.chm.myfinances.testsupport.web;

/**
 * The credential the test JVM boots with (F036): {@code build.gradle} hands the app {@link
 * #USERNAME} and the Argon2 hash of {@link #PASSWORD} through {@code myfinances.auth.*} system
 * properties, so a REST test that wants to exercise a real login uses these.
 */
public final class TestCredentials {

  public static final String USERNAME = "test-user";
  public static final String PASSWORD = "test-password-f036";

  private TestCredentials() {}
}
