package com.chm.myfinances.infrastructure.web;

import java.util.Map;
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
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception)
      throws Exception {
    boolean hasResponseStatus =
        AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class)
            != null;
    if (hasResponseStatus || exception instanceof ErrorResponse) {
      // Not "unexpected" - either one of this app's own deliberate @ResponseStatus mappings, or
      // one of Spring's own well-known MVC exceptions with a built-in mapping. Rethrowing the
      // same instance makes Spring's exception-handling machinery fall through to the next
      // resolver in its chain instead of treating this as a handled response.
      throw exception;
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("message", "An unexpected error occurred"));
  }
}
