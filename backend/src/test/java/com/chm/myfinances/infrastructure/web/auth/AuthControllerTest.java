package com.chm.myfinances.infrastructure.web.auth;

import static com.chm.myfinances.testsupport.web.MockMvcSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.TestCredentials;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * F036: the authentication contract end to end through the real Spring Security filter chain -
 * login/logout/session, the {@code unauthenticated}/{@code access-denied} {@code ProblemDetail}
 * shapes, CSRF, and "every other endpoint is closed without a session". Each test logs in from its
 * own fake client address so the in-memory login-attempt tracker (one per context) never couples
 * tests together.
 */
@WebIntegrationTest
class AuthControllerTest {

  private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private MockMvc mockMvc;
  private RequestPostProcessor client;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.buildUnauthenticated(webApplicationContext);
    String address = "10.36.0." + NEXT_CLIENT.getAndIncrement();
    client =
        request -> {
          request.setRemoteAddr(address);
          return request;
        };
  }

  private static String loginBody(String username, String password) throws Exception {
    return JsonSupport.toJson(Map.of("username", username, "password", password));
  }

  private MvcResult login(MockHttpSession session, String username, String password)
      throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/login")
                .with(client)
                .with(csrfToken())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, password)))
        .andReturn();
  }

  private MockHttpSession loggedInSession() throws Exception {
    MockHttpSession session = new MockHttpSession();
    MvcResult result = login(session, TestCredentials.USERNAME, TestCredentials.PASSWORD);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return session;
  }

  // --- login / session / logout -------------------------------------------------------------

  @Test
  void sessionIsUnauthorizedWithAnUnauthenticatedProblemBeforeLogin() throws Exception {
    mockMvc
        .perform(get("/api/auth/session"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:unauthenticated"))
        .andExpect(jsonPath("$.title").value("Unauthenticated"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/api/auth/session"))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void loginWithCorrectCredentialsEstablishesASessionAndReturns200() throws Exception {
    MockHttpSession session = new MockHttpSession();

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(client)
                .with(csrfToken())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(TestCredentials.USERNAME, TestCredentials.PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true));

    mockMvc
        .perform(get("/api/auth/session").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true));
  }

  @Test
  void aWrongPasswordIsRejectedWithTheUnauthenticatedProblem() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(client)
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(TestCredentials.USERNAME, "definitely-not-it")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:unauthenticated"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/api/auth/login"))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void aWrongUsernameIsIndistinguishableFromAWrongPassword() throws Exception {
    String wrongPassword =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(client)
                    .with(csrfToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(TestCredentials.USERNAME, "definitely-not-it")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    MvcResult wrongUsername =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(client)
                    .with(csrfToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody("somebody-else", TestCredentials.PASSWORD)))
            .andReturn();

    assertThat(wrongUsername.getResponse().getStatus()).isEqualTo(401);
    assertThat(wrongUsername.getResponse().getContentAsString()).isEqualTo(wrongPassword);
  }

  @Test
  void aFailedLoginDoesNotCreateAnAuthenticatedSession() throws Exception {
    MockHttpSession session = new MockHttpSession();
    login(session, TestCredentials.USERNAME, "definitely-not-it");

    mockMvc.perform(get("/api/auth/session").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void aBlankOrMissingFieldIsAValidationErrorNotALoginAttempt() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(client)
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("", "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:method-argument-not-valid"));
  }

  @Test
  void logoutInvalidatesTheSession() throws Exception {
    MockHttpSession session = loggedInSession();

    mockMvc
        .perform(post("/api/auth/logout").with(csrfToken()).session(session))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/auth/session").session(session)).andExpect(status().isUnauthorized());
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void logoutWithoutASessionIsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").with(csrfToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:unauthenticated"));
  }

  @Test
  void loggingInRotatesTheSessionId() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String before = session.getId();

    login(session, TestCredentials.USERNAME, TestCredentials.PASSWORD);

    assertThat(session.getId()).isNotEqualTo(before);
  }

  // --- everything else is closed without a session -------------------------------------------

  @Test
  void everyApiEndpointRequiresASessionExceptLogin() throws Exception {
    Set<String> checked = new HashSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      if (info.getPathPatternsCondition() == null) {
        continue;
      }
      for (var pattern : info.getPathPatternsCondition().getPatterns()) {
        String path = pattern.getPatternString();
        if (!path.startsWith("/api/")) {
          continue;
        }
        Set<org.springframework.web.bind.annotation.RequestMethod> methods =
            info.getMethodsCondition().getMethods();
        Iterable<HttpMethod> httpMethods =
            methods.isEmpty()
                ? Set.of(HttpMethod.GET)
                : methods.stream().map(m -> HttpMethod.valueOf(m.name())).toList();
        for (HttpMethod method : httpMethods) {
          if (method == HttpMethod.POST && path.equals("/api/auth/login")) {
            continue;
          }
          String concretePath = path.replaceAll("\\{[^/}]+}", UUID.randomUUID().toString());
          mockMvc
              .perform(request(method, concretePath).with(csrfToken()))
              .andExpect(status().isUnauthorized())
              .andExpect(jsonPath("$.type").value("urn:my-finances:error:unauthenticated"));
          checked.add(method + " " + path);
        }
      }
    }
    // Guards the enumeration itself: if the mapping lookup silently found nothing, every assertion
    // above would pass vacuously.
    assertThat(checked).hasSizeGreaterThan(50);
    assertThat(checked).contains("GET /api/accounts", "GET /api/health", "GET /api/auth/session");
  }

  @Test
  void anExistingEndpointSucceedsOnceLoggedIn() throws Exception {
    MockHttpSession session = loggedInSession();

    mockMvc.perform(get("/api/categories").session(session)).andExpect(status().isOk());
  }

  @Test
  void theApiDocsAndSwaggerUiAreAlsoBehindTheLogin() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
  }

  @Test
  void theHealthcheckAnswersWithoutASession() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  // --- CSRF ---------------------------------------------------------------------------------

  @Test
  void aStateChangingRequestWithoutACsrfTokenIsRefusedWithAnAccessDeniedProblem() throws Exception {
    MockHttpSession session = loggedInSession();

    mockMvc
        .perform(
            post("/api/categories")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CSRF Test\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:access-denied"))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.instance").value("/api/categories"))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void theCsrfCookieIssuedOnAGetAuthorizesALoginPost() throws Exception {
    // What the SPA does: its boot-time GET /api/auth/session (401) already hands out the cookie...
    MvcResult boot = mockMvc.perform(get("/api/auth/session")).andReturn();
    Cookie csrfCookie = boot.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrfCookie).as("XSRF-TOKEN cookie on an unauthenticated GET").isNotNull();
    assertThat(csrfCookie.isHttpOnly()).isFalse();

    // ...and the login POST echoes it back as the X-XSRF-TOKEN header, no other carve-out.
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(client)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(TestCredentials.USERNAME, TestCredentials.PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  void aWrongCsrfHeaderIsRefused() throws Exception {
    MvcResult boot = mockMvc.perform(get("/api/auth/session")).andReturn();
    Cookie csrfCookie = boot.getResponse().getCookie("XSRF-TOKEN");

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(client)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", "not-the-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(TestCredentials.USERNAME, TestCredentials.PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:access-denied"));
  }
}
