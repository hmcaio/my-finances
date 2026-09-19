package com.chm.myfinances.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cross-cutting safety net for anything the app doesn't explicitly plan for: guarantees, at the
 * code level (not just via {@code spring.web.error.include-*} config in {@code application.yml}),
 * that an unexpected exception's message/class name/stack trace never reaches an HTTP response
 * (security-audit fix).
 *
 * <p>Deliberately does <b>not</b> touch any error mapping Spring/this app already handles
 * explicitly for that exception type - only genuinely unforeseen bugs/errors get the generic 500
 * below. Two kinds of exception are rethrown instead, so a later resolver in Spring's exception
 * resolver chain handles them exactly as it does today:
 *
 * <ul>
 *   <li>Exceptions carrying a {@link ResponseStatus} annotation - this app's own {@code
 *       {Aggregate}NotFoundException} pattern (e.g. {@code CategoryNotFoundException}) for expected
 *       404s. Future features needing another *expected* error mapping should keep using
 *       {@code @ResponseStatus} on their own exception class rather than adding a second catch-all
 *       advice here.
 *   <li>Exceptions implementing Spring's {@link ErrorResponse} contract - the standard Spring MVC
 *       exceptions (e.g. {@code NoResourceFoundException} for an unmapped route, {@code
 *       HttpRequestMethodNotSupportedException}) that report their own status programmatically
 *       rather than via {@code @ResponseStatus}.
 * </ul>
 *
 * <p>Logging (F016, ADR 0011): an unexpected exception is logged at ERROR with its full stack trace
 * before the generic body is returned - the response contract is unchanged. A rethrown {@link
 * ResponseStatus} exception is logged at INFO with its class simple name only, <b>not</b> its
 * message: the backend sends no body text for these, so the class name is the only way to see which
 * 409 happened. Spring's own {@link ErrorResponse} exceptions are not logged here - the access
 * line's status is enough.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(
      Exception exception, HttpServletRequest request) throws Exception {
    boolean hasResponseStatus =
        AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class)
            != null;
    if (hasResponseStatus) {
      // Class name only - never the message (it can carry ids/names, and the client never gets it).
      log.info(
          "{} on {} {}",
          exception.getClass().getSimpleName(),
          request.getMethod(),
          request.getRequestURI());
    }
    if (hasResponseStatus || exception instanceof ErrorResponse) {
      // Not "unexpected" - either one of this app's own deliberate @ResponseStatus mappings, or
      // one of Spring's own well-known MVC exceptions with a built-in mapping. Rethrowing the
      // same instance makes Spring's exception-handling machinery fall through to the next
      // resolver in its chain instead of treating this as a handled response.
      throw exception;
    }
    log.error(
        "Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("message", "An unexpected error occurred"));
  }
}
