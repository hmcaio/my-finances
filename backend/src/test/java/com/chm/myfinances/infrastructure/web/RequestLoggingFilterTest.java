package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.LogCapture;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit test for {@link RequestLoggingFilter} (F016, written first per ADR 0004): plain {@code
 * Mock*} servlet objects and {@link FakeIdGenerator}, no Spring context.
 */
class RequestLoggingFilterTest {

  private static final String HEADER = "X-Request-Id";
  private static final UUID GENERATED_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private final RequestLoggingFilter filter =
      new RequestLoggingFilter(new FakeIdGenerator(GENERATED_ID));

  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/things");
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void generatesAnIdFromTheIdGeneratorWhenNoneIsSent() throws Exception {
    List<String> idSeenByChain = new ArrayList<>();

    filter.doFilter(request, response, (req, res) -> idSeenByChain.add(MDC.get("requestId")));

    assertThat(idSeenByChain).containsExactly(GENERATED_ID.toString());
    assertThat(response.getHeader(HEADER)).isEqualTo(GENERATED_ID.toString());
  }

  @Test
  void keepsAValidInboundId() throws Exception {
    request.addHeader(HEADER, "abc-123-DEF");
    List<String> idSeenByChain = new ArrayList<>();

    filter.doFilter(request, response, (req, res) -> idSeenByChain.add(MDC.get("requestId")));

    assertThat(idSeenByChain).containsExactly("abc-123-DEF");
    assertThat(response.getHeader(HEADER)).isEqualTo("abc-123-DEF");
  }

  @Test
  void keepsAnInboundIdOfExactlyTheMaximumLength() throws Exception {
    String maxLength = "a".repeat(64);
    request.addHeader(HEADER, maxLength);

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(HEADER)).isEqualTo(maxLength);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "bad\r\nX-Injected: 1", // CR/LF header/log injection
        "line1\nline2",
        "abc-123\n", // a trailing newline must not slip past the $ anchor
        "", // blank
        "abc_123", // underscore is not in the allowed alphabet
        "has space",
        "<script>alert(1)</script>",
        "id%0d%0aX-Injected: 1",
        "ünïcode"
      })
  void replacesAMalformedInboundId(String inbound) throws Exception {
    request.addHeader(HEADER, inbound);

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(HEADER)).isEqualTo(GENERATED_ID.toString());
  }

  @Test
  void replacesAnOverlongInboundId() throws Exception {
    request.addHeader(HEADER, "a".repeat(65));

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(HEADER)).isEqualTo(GENERATED_ID.toString());
  }

  @Test
  void clearsTheMdcAfterTheRequest() throws Exception {
    filter.doFilter(request, response, (req, res) -> {});

    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void clearsTheMdcEvenWhenTheChainThrows() {
    FilterChain failing =
        (req, res) -> {
          throw new ServletException("boom");
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, failing))
        .isInstanceOf(ServletException.class);

    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void logsOneAccessLineWithMethodPathStatusAndDuration() throws Exception {
    request.setMethod("POST");
    request.setRequestURI("/api/transactions");

    try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
      filter.doFilter(
          request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(201));

      List<ILoggingEvent> info = logs.eventsAt(Level.INFO);
      assertThat(info).hasSize(1);
      assertThat(info.get(0).getFormattedMessage())
          .matches("POST /api/transactions -> 201 \\(\\d+ ms\\)");
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void accessLineCarriesTheRequestIdInTheMdc() throws Exception {
    request.addHeader(HEADER, "trace-me-1");

    try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
      filter.doFilter(request, response, (req, res) -> {});

      assertThat(logs.eventsAt(Level.INFO).get(0).getMDCPropertyMap())
          .containsEntry("requestId", "trace-me-1");
    }
  }

  @Test
  void accessLineNeverIncludesTheQueryString() throws Exception {
    request.setRequestURI("/api/transactions");
    request.setQueryString("accountId=secret-account-id&from=2026-01-01");

    try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
      filter.doFilter(request, response, (req, res) -> {});

      assertThat(logs.messagesAt(Level.INFO))
          .singleElement()
          .satisfies(
              message ->
                  assertThat(message)
                      .contains("/api/transactions")
                      .doesNotContain("?")
                      .doesNotContain("secret-account-id")
                      .doesNotContain("2026-01-01"));
    }
  }

  @Test
  void actuatorRequestsLogAtDebugNotInfo() throws Exception {
    request.setRequestURI("/actuator/health");

    try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
      filter.doFilter(request, response, (req, res) -> {});

      assertThat(logs.eventsAt(Level.INFO)).isEmpty();
      assertThat(logs.messagesAt(Level.DEBUG))
          .singleElement()
          .satisfies(message -> assertThat(message).startsWith("GET /actuator/health -> 200"));
    }
  }

  @Test
  void aPathThatMerelyStartsWithActuatorStillLogsAtInfo() throws Exception {
    request.setRequestURI("/actuatorish");

    try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
      filter.doFilter(request, response, (req, res) -> {});

      assertThat(logs.eventsAt(Level.INFO)).hasSize(1);
    }
  }

  @Test
  void stillLogsAnAccessLineWhenTheChainThrows() {
    FilterChain failing =
        (req, res) -> {
          throw new ServletException("boom");
        };

    try (LogCapture logs = LogCapture.of(RequestLoggingFilter.class)) {
      assertThatThrownBy(() -> filter.doFilter(request, response, failing))
          .isInstanceOf(ServletException.class);

      assertThat(logs.messagesAt(Level.INFO))
          .singleElement()
          .satisfies(message -> assertThat(message).startsWith("GET /api/things -> 500"));
    }
  }
}
