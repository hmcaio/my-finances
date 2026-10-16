package com.chm.myfinances.infrastructure.auth;

import com.chm.myfinances.infrastructure.web.ProblemTypeSlugs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * The {@code 403 access-denied} body (F036, ADR 0029) for a request Spring Security refuses after
 * the fact - in this single-role app that is a failed CSRF check on a state-changing request. See
 * {@link ProblemDetailAuthenticationEntryPoint} for why this is a filter-chain component and not
 * part of {@code GlobalExceptionHandler}.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    ProblemDetailResponseWriter.write(
        request,
        response,
        HttpServletResponse.SC_FORBIDDEN,
        ProblemTypeSlugs.ACCESS_DENIED_TYPE,
        ProblemTypeSlugs.ACCESS_DENIED_TITLE);
  }
}
