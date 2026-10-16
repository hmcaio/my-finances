package com.chm.myfinances.infrastructure.auth;

import com.chm.myfinances.infrastructure.web.ProblemTypeSlugs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * The {@code 401 unauthenticated} body (F036, ADR 0029): what Spring Security's filter chain sends
 * for a request that needs a session and has none, and what the login endpoint sends for wrong
 * credentials (it calls this directly, so a bad username and a bad password are literally the same
 * response).
 *
 * <p>One of the two deliberate exceptions to "{@code GlobalExceptionHandler} builds every error
 * body" (ADR 0026): the security filter chain runs before MVC dispatch, so no
 * {@code @ControllerAdvice} can see these. It is a filter-chain component, not an advice, so the
 * one-{@code @RestControllerAdvice} rule is untouched. Logs nothing: the access line already
 * records the 401.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ProblemDetailResponseWriter.write(
        request,
        response,
        HttpServletResponse.SC_UNAUTHORIZED,
        ProblemTypeSlugs.UNAUTHENTICATED_TYPE,
        ProblemTypeSlugs.UNAUTHENTICATED_TITLE);
  }
}
