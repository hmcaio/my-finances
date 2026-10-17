package com.chm.myfinances.testsupport.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The one-line {@link MockMvc} build every REST-layer test repeats in its own {@code @BeforeEach}
 * (Spring Boot 4.x removed {@code @AutoConfigureMockMvc} - see {@code
 * docs/conventions/backend-testing.md} for why it's built by hand). Extracted for issue #31, B7,
 * mainly so a future change to how {@link MockMvc} is built (e.g. registering a filter) has one
 * place to land.
 *
 * <p>F036: every API endpoint now sits behind Spring Security, so {@link #build} applies the real
 * security filter chain and pre-authenticates every request - a pre-populated session holding an
 * authenticated context, plus a matching {@code XSRF-TOKEN} cookie / {@code X-XSRF-TOKEN} header
 * pair - so an existing controller test keeps testing the controller while an unauthenticated
 * request can't slip through by the chain being absent. A test of authentication itself uses {@link
 * #buildUnauthenticated} and brings its own session and CSRF pair ({@link #csrfToken()}).
 *
 * <p>Deliberately not {@code SecurityMockMvcRequestPostProcessors.csrf()}/{@code user()}: those
 * rewire the shared, context-cached filter chain's CSRF/context repositories (the CSRF one becomes
 * session-based, so no {@code XSRF-TOKEN} cookie is ever written again in that JVM), which would
 * make the cookie behaviour under test depend on which test class happened to run first.
 */
public final class MockMvcSupport {

  private static final String CSRF_TOKEN = "test-csrf-token";

  private MockMvcSupport() {}

  /** Real security filter chain; each request is already authenticated and CSRF-valid. */
  public static MockMvc build(WebApplicationContext webApplicationContext) {
    return MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .defaultRequest(get("/").session(authenticatedSession()).with(csrfToken()))
        .build();
  }

  /** Real security filter chain and no default authentication: requests start logged out. */
  public static MockMvc buildUnauthenticated(WebApplicationContext webApplicationContext) {
    return MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  /**
   * A valid CSRF pair as the real SPA sends it: the cookie Spring Security issued, echoed back as
   * the header. {@code CookieCsrfTokenRepository} is stateless, so any matching pair is accepted.
   */
  public static RequestPostProcessor csrfToken() {
    return request -> {
      request.setCookies(appendCookie(request.getCookies()));
      request.addHeader("X-XSRF-TOKEN", CSRF_TOKEN);
      return request;
    };
  }

  private static Cookie[] appendCookie(Cookie[] existing) {
    ArrayList<Cookie> cookies = new ArrayList<>();
    if (existing != null) {
      cookies.addAll(List.of(existing));
    }
    cookies.add(new Cookie("XSRF-TOKEN", CSRF_TOKEN));
    return cookies.toArray(Cookie[]::new);
  }

  private static MockHttpSession authenticatedSession() {
    SecurityContext context = new SecurityContextImpl();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            TestCredentials.USERNAME, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    return session;
  }
}
