package com.chm.myfinances.infrastructure.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes sure the {@code XSRF-TOKEN} cookie is written on every response that needs one (F036, ADR
 * 0029). Spring Security loads the CSRF token lazily, so on a plain {@code GET} nothing would touch
 * it and the cookie would never reach the browser - the SPA's boot-time {@code GET
 * /api/auth/session} would then have no token to echo back as {@code X-XSRF-TOKEN} on the login
 * {@code POST}. Reading the token's value here forces the repository to generate and save it (and
 * to re-issue it after the cookie is lost, e.g. a browser restart).
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      token.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
