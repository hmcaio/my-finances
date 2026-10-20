package com.chm.myfinances.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Writes an RFC 9457 {@code ProblemDetail}-shaped body (ADR 0026) straight onto the response, for
 * the two Spring Security hooks that run in the filter chain before MVC dispatch and so can never
 * reach {@code GlobalExceptionHandler} (F036, ADR 0029). Same four members every other error
 * response carries - {@code type}/{@code title}/{@code status}/{@code instance} - and no {@code
 * detail}, ever (ADR 0011). Hand-written JSON on purpose: it is four fixed-shape members, {@code
 * type}/{@code title} are constants, and only {@code instance} (the request path) needs escaping,
 * which keeps this independent of which Jackson the MVC layer happens to be configured with.
 */
final class ProblemDetailResponseWriter {

  private ProblemDetailResponseWriter() {}

  static void write(
      HttpServletRequest request, HttpServletResponse response, int status, URI type, String title)
      throws IOException {
    String body =
        "{\"type\":\""
            + escape(type.toString())
            + "\",\"title\":\""
            + escape(title)
            + "\",\"status\":"
            + status
            + ",\"instance\":\""
            + escape(request.getRequestURI())
            + "\"}";
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
  }

  private static String escape(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        default -> {
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
        }
      }
    }
    return escaped.toString();
  }
}
