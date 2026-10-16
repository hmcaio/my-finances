package com.chm.myfinances.infrastructure.web.auth;

import static com.chm.myfinances.testsupport.web.MockMvcSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chm.myfinances.testsupport.LogCapture;
import com.chm.myfinances.testsupport.MutableClock;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.TestCredentials;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * F036: five failed logins from one client start a cooldown during which even the correct
 * credential is refused with {@code 429 too-many-login-attempts}; once the window has elapsed a
 * correct login succeeds again. Time is the app's one {@code Clock} bean, pinned here with a {@link
 * MutableClock} - no sleeping. Also pins the logging rule: login outcomes are logged as outcome and
 * count only, never the username or password (ADR 0011).
 */
@WebIntegrationTest
@Import(LoginRateLimitTest.FixedClockTestConfig.class)
class LoginRateLimitTest {

  @TestConfiguration(proxyBeanMethods = false)
  public static class FixedClockTestConfig {
    @Bean
    @Primary
    public MutableClock loginRateLimitTestClock() {
      return new MutableClock();
    }
  }

  private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");
  private static final String ATTACKER_PASSWORD = "an-attackers-guess-xyzzy";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private MutableClock clock;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.buildUnauthenticated(webApplicationContext);
    clock.set(T0, ZoneOffset.UTC);
  }

  @AfterEach
  void tearDown() {
    clock.reset();
  }

  private void attempt(String clientAddress, String username, String password, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(
                    request -> {
                      request.setRemoteAddr(clientAddress);
                      return request;
                    })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonSupport.toJson(Map.of("username", username, "password", password))))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  void fiveFailuresStartACooldownThatRefusesEvenTheCorrectPassword() throws Exception {
    String client = "10.37.0.1";
    for (int i = 0; i < 5; i++) {
      attempt(client, TestCredentials.USERNAME, ATTACKER_PASSWORD, 401);
    }

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(
                    request -> {
                      request.setRemoteAddr(client);
                      return request;
                    })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JsonSupport.toJson(
                        Map.of(
                            "username",
                            TestCredentials.USERNAME,
                            "password",
                            TestCredentials.PASSWORD))))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:too-many-login-attempts"))
        .andExpect(jsonPath("$.title").value("Too many login attempts"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.instance").value("/api/auth/login"))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void aCorrectLoginSucceedsAgainOnceTheCooldownHasElapsed() throws Exception {
    String client = "10.37.0.2";
    for (int i = 0; i < 5; i++) {
      attempt(client, TestCredentials.USERNAME, ATTACKER_PASSWORD, 401);
    }
    attempt(client, TestCredentials.USERNAME, TestCredentials.PASSWORD, 429);

    clock.set(T0.plusSeconds(16 * 60), ZoneOffset.UTC);

    attempt(client, TestCredentials.USERNAME, TestCredentials.PASSWORD, 200);
  }

  @Test
  void theCooldownIsPerClient() throws Exception {
    for (int i = 0; i < 5; i++) {
      attempt("10.37.0.3", TestCredentials.USERNAME, ATTACKER_PASSWORD, 401);
    }

    attempt("10.37.0.4", TestCredentials.USERNAME, TestCredentials.PASSWORD, 200);
  }

  @Test
  void aSuccessfulLoginResetsTheFailureCount() throws Exception {
    String client = "10.37.0.5";
    for (int i = 0; i < 4; i++) {
      attempt(client, TestCredentials.USERNAME, ATTACKER_PASSWORD, 401);
    }
    attempt(client, TestCredentials.USERNAME, TestCredentials.PASSWORD, 200);

    for (int i = 0; i < 4; i++) {
      attempt(client, TestCredentials.USERNAME, ATTACKER_PASSWORD, 401);
    }
    attempt(client, TestCredentials.USERNAME, TestCredentials.PASSWORD, 200);
  }

  @Test
  void loginAttemptsLogOutcomeAndCountButNeverTheCredential() throws Exception {
    try (LogCapture capture = LogCapture.of(AuthController.class)) {
      attempt("10.37.0.6", TestCredentials.USERNAME, ATTACKER_PASSWORD, 401);
      attempt("10.37.0.6", "an-unknown-username", ATTACKER_PASSWORD, 401);
      attempt("10.37.0.6", TestCredentials.USERNAME, TestCredentials.PASSWORD, 200);

      assertThat(capture.events()).extracting(ILoggingEvent::getLevel).containsOnly(Level.INFO);
      String everything =
          capture.events().stream().map(ILoggingEvent::getFormattedMessage).toList().toString();
      assertThat(everything)
          .contains("Login failed")
          .contains("Login succeeded")
          .doesNotContain(TestCredentials.USERNAME)
          .doesNotContain("an-unknown-username")
          .doesNotContain(ATTACKER_PASSWORD)
          .doesNotContain(TestCredentials.PASSWORD)
          .doesNotContain("10.37.0.6");
    }
  }
}
