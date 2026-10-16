package com.chm.myfinances.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Establishes and tears down the login session for the JSON login/logout endpoints (F036, ADR 0029)
 * - the part of {@code formLogin()} that Spring Security would otherwise do for a form post, done
 * explicitly because this app's login is a JSON {@code POST} handled by a controller: authenticate,
 * rotate the session id (session fixation) and the CSRF token, then save the context into the same
 * {@link SecurityContextRepository} the filter chain reads it from.
 */
@Component
public class SessionAuthenticator {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();
  private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

  public SessionAuthenticator(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      CsrfTokenRepository csrfTokenRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.sessionAuthenticationStrategy =
        new CompositeSessionAuthenticationStrategy(
            List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
  }

  /**
   * Verifies the credential and, on success, stores the authenticated context in the session.
   *
   * @throws AuthenticationException on any credential failure - never says which field was wrong
   */
  public void login(
      HttpServletRequest request, HttpServletResponse response, String username, String password) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextHolderStrategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }

  /** Invalidates the session and clears the security context. Safe with no session. */
  public void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    logoutHandler.logout(request, response, authentication);
  }
}
