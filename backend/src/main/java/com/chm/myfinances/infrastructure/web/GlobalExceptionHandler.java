package com.chm.myfinances.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cross-cutting safety net that builds the RFC 9457 Problem Details body (ADR 0026, F031) for
 * <b>every</b> error response the app returns - its own {@code @ResponseStatus} exceptions,
 * Spring's own {@link ErrorResponse} exceptions, the two 400-mapped exceptions that are neither
 * ({@link HttpMessageNotReadableException}/{@link TypeMismatchException}), and the generic
 * unexpected-exception fallback - and guarantees, at the code level (not just via {@code
 * spring.web.error.include-*} config in {@code application.yml}), that an unexpected exception's
 * message/class name/stack trace never reaches the HTTP response (security-audit fix, ADR 0011).
 *
 * <p>Status resolution, in order:
 *
 * <ul>
 *   <li>A merged {@link ResponseStatus} annotation's value - this app's own {@code
 *       {Aggregate}NotFoundException}/{@code {Aggregate}AlreadyExistsException}-style expected
 *       cases.
 *   <li>{@link ErrorResponse#getStatusCode()} - the standard Spring MVC exceptions (e.g. {@code
 *       NoResourceFoundException} for an unmapped route, {@code
 *       HttpRequestMethodNotSupportedException}) that report their own status programmatically
 *       rather than via {@code @ResponseStatus}.
 *   <li>A hardcoded {@code 400} for {@link HttpMessageNotReadableException} (malformed/empty JSON
 *       body, bad enum value, wrong JSON type) and {@link TypeMismatchException} (unparseable
 *       UUID/date in the path or query string, e.g. {@code MethodArgumentTypeMismatchException}) -
 *       matching {@code DefaultHandlerExceptionResolver}'s own mapping for the two client-error
 *       exceptions that implement neither of the above (issue #19).
 *   <li>{@code 500} for anything else - genuinely unforeseen bugs/errors.
 * </ul>
 *
 * <p>Every branch gets a {@link ProblemDetail} body: {@code type}/{@code title} derived from the
 * exception's class name via {@link ProblemTypeSlugs} (fixed {@code internal-error} constants for
 * the unexpected-exception branch, since that set is unbounded), {@code instance} set to the
 * request's path, and {@code detail} always left {@code null} - the one field RFC 9457 expects to
 * carry human-readable specifics, and exactly the field ADR 0011 forbids populating with exception
 * text or entity data. No branch rethrows to Spring's default resolver chain.
 *
 * <p>Logging (F016, ADR 0011) is unchanged in substance: an unexpected exception is logged at ERROR
 * with its full stack trace; an exception carrying {@code @ResponseStatus} is logged at INFO with
 * its class simple name only, <b>not</b> its message - the backend sends no exception text, so the
 * class name (now also the {@code type} it emits, derived from that same name - no new leak
 * surface) is the only way to see which case fired. Spring's own {@link ErrorResponse} exceptions
 * are not logged here - the access line's status is enough.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    ResponseStatus responseStatus =
        AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);

    HttpStatusCode status;
    URI type;
    String title;

    if (responseStatus != null) {
      status = responseStatus.value();
      type = ProblemTypeSlugs.typeUriFor(exception.getClass());
      title = ProblemTypeSlugs.titleFor(exception.getClass());
      // Class name only - never the message (it can carry ids/names, and the client never gets it).
      log.info(
          "{} on {} {}",
          exception.getClass().getSimpleName(),
          request.getMethod(),
          request.getRequestURI());
    } else if (exception instanceof ErrorResponse errorResponse) {
      status = errorResponse.getStatusCode();
      type = ProblemTypeSlugs.typeUriFor(exception.getClass());
      title = ProblemTypeSlugs.titleFor(exception.getClass());
    } else if (exception instanceof HttpMessageNotReadableException
        || exception instanceof TypeMismatchException) {
      status = HttpStatus.BAD_REQUEST;
      type = ProblemTypeSlugs.typeUriFor(exception.getClass());
      title = ProblemTypeSlugs.titleFor(exception.getClass());
    } else {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      type = ProblemTypeSlugs.INTERNAL_ERROR_TYPE;
      title = ProblemTypeSlugs.INTERNAL_ERROR_TITLE;
      log.error(
          "Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
    }

    ProblemDetail problemDetail = ProblemDetail.forStatus(status);
    problemDetail.setType(type);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(status).body(problemDetail);
  }
}
