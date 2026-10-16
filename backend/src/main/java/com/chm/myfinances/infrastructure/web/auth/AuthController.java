package com.chm.myfinances.infrastructure.web.auth;

import com.chm.myfinances.infrastructure.auth.ClientAddressResolver;
import com.chm.myfinances.infrastructure.auth.LoginAttemptTracker;
import com.chm.myfinances.infrastructure.auth.SessionAuthenticator;
import com.chm.myfinances.infrastructure.auth.TooManyLoginAttemptsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout and session-check (F036, ADR 0029). The frontend calls {@code GET
 * /api/auth/session} on boot to learn whether to show the app or the login page; a {@code 401}
 * there is the normal "not logged in" answer, not a failure.
 *
 * <p>Only {@code login} is open to unauthenticated callers (see {@code SecurityConfig}); {@code
 * logout} and {@code session} need a session like everything else, so an expired session shows up
 * as the same {@code 401 unauthenticated} the frontend already redirects on.
 *
 * <p>Logging is outcome and count only (ADR 0011): never the username, the password or the client
 * address.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final SessionAuthenticator sessionAuthenticator;
  private final LoginAttemptTracker loginAttemptTracker;
  private final ClientAddressResolver clientAddressResolver;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  public AuthController(
      SessionAuthenticator sessionAuthenticator,
      LoginAttemptTracker loginAttemptTracker,
      ClientAddressResolver clientAddressResolver,
      AuthenticationEntryPoint authenticationEntryPoint) {
    this.sessionAuthenticator = sessionAuthenticator;
    this.loginAttemptTracker = loginAttemptTracker;
    this.clientAddressResolver = clientAddressResolver;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  /**
   * Wrong credentials answer through the same {@code AuthenticationEntryPoint} that guards every
   * other endpoint, so the body is the plain {@code unauthenticated} problem and never says which
   * of username/password was wrong. Returns {@code null} in that case: the response is already
   * written.
   */
  @PostMapping("/login")
  @Operation(summary = "Log in with the single shared credential, starting a session")
  @ApiResponse(responseCode = "200", description = "Session established")
  @ApiResponse(
      responseCode = "401",
      description = "Wrong credentials - type `unauthenticated`",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Too many failed attempts from this client - type `too-many-login-attempts`",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public SessionResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException, ServletException {
    String client = clientAddressResolver.resolve(request);
    if (loginAttemptTracker.isLocked(client)) {
      throw new TooManyLoginAttemptsException();
    }
    try {
      sessionAuthenticator.login(request, response, body.username(), body.password());
    } catch (AuthenticationException failure) {
      int failures = loginAttemptTracker.recordFailure(client);
      log.info("Login failed ({} consecutive failures from this client)", failures);
      authenticationEntryPoint.commence(request, response, failure);
      return null;
    }
    loginAttemptTracker.recordSuccess(client);
    log.info("Login succeeded");
    return new SessionResponse(true);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "End the current session")
  @ApiResponse(responseCode = "204", description = "Session ended")
  @ApiResponse(
      responseCode = "401",
      description = "No session - type `unauthenticated`",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void logout(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    sessionAuthenticator.logout(request, response, authentication);
    log.info("Logged out");
  }

  @GetMapping("/session")
  @Operation(summary = "Whether the caller has a session (the frontend's boot-time auth check)")
  @ApiResponse(responseCode = "200", description = "A session exists")
  @ApiResponse(
      responseCode = "401",
      description = "No session - type `unauthenticated`",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public SessionResponse session() {
    // Reaching this method at all means the filter chain found an authenticated session.
    return new SessionResponse(true);
  }
}
