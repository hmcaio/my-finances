package com.chm.myfinances.infrastructure.web;

import com.chm.myfinances.domain.shared.IdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id and writes one access-log line per request (F016, ADR 0011).
 *
 * <p>The id is the caller's {@code X-Request-Id} if it matches {@code ^[A-Za-z0-9-]{1,64}$},
 * otherwise a fresh one from the {@link IdGenerator} port (ADR 0005). The pattern is the
 * log-forging / header-injection guard: the value ends up in log lines and in a response header, so
 * anything else (CR/LF, spaces, over-long values) is replaced rather than sanitized. It is put in
 * the SLF4J MDC as {@code requestId} (rendered by {@code logging.pattern.correlation}), echoed as
 * the response header, and removed in {@code finally}.
 *
 * <p>The access line is method + path + status + duration only: never the query string (filters may
 * carry ids/dates) and never a request/response body. {@code /actuator/**} is logged at DEBUG
 * because the prod compose healthcheck hits it every few seconds.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the MDC is set before anything else logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_HEADER = "X-Request-Id";

  /**
   * Public (F025, ADR 0022) so {@code AuditLogRepositoryAdapter} can read the same key when it
   * stamps an {@code AuditEntry}'s {@code requestId} from the MDC - the only other reader of this
   * constant outside this filter.
   */
  public static final String MDC_KEY = "requestId";

  private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private final IdGenerator idGenerator;

  public RequestLoggingFilter(IdGenerator idGenerator) {
    this.idGenerator = idGenerator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    MDC.put(MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    long startNanos = System.nanoTime();
    boolean completed = false;
    try {
      filterChain.doFilter(request, response);
      completed = true;
    } finally {
      // An exception escaping the chain is turned into a 500 by the container after this filter
      // unwinds; the response status is still the default 200 at this point, so report 500.
      int status = completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
      logAccess(request, status, (System.nanoTime() - startNanos) / 1_000_000);
      MDC.remove(MDC_KEY);
    }
  }

  private String resolveRequestId(String inbound) {
    if (inbound != null && VALID_REQUEST_ID.matcher(inbound).matches()) {
      return inbound;
    }
    return idGenerator.newId().toString();
  }

  private void logAccess(HttpServletRequest request, int status, long durationMillis) {
    // getRequestURI() is the path only - the query string is deliberately never logged.
    String path = request.getRequestURI();
    if (isActuator(path)) {
      log.debug("{} {} -> {} ({} ms)", request.getMethod(), path, status, durationMillis);
    } else {
      log.info("{} {} -> {} ({} ms)", request.getMethod(), path, status, durationMillis);
    }
  }

  private static boolean isActuator(String path) {
    return path.equals("/actuator") || path.startsWith("/actuator/");
  }
}
