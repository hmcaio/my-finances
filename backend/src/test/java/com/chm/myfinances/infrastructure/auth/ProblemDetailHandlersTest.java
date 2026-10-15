package com.chm.myfinances.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * F036: the two filter-chain hooks that can't go through {@code GlobalExceptionHandler} still emit
 * the same RFC 9457 shape - {@code type}/{@code title}/{@code status}/{@code instance}, no {@code
 * detail} - and never echo the exception's message.
 */
class ProblemDetailHandlersTest {

  @Test
  void entryPointWritesTheUnauthenticatedProblem() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ProblemDetailAuthenticationEntryPoint()
        .commence(request, response, new BadCredentialsException("secret internal reason"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"urn:my-finances:error:unauthenticated\",\"title\":\"Unauthenticated\","
                + "\"status\":401,\"instance\":\"/api/accounts\"}");
  }

  @Test
  void accessDeniedHandlerWritesTheAccessDeniedProblem() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/transactions");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ProblemDetailAccessDeniedHandler()
        .handle(request, response, new AccessDeniedException("Invalid CSRF Token 'abc'"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"urn:my-finances:error:access-denied\",\"title\":\"Access denied\","
                + "\"status\":403,\"instance\":\"/api/transactions\"}");
  }

  @Test
  void neverEchoesTheExceptionMessage() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ProblemDetailAuthenticationEntryPoint()
        .commence(
            new MockHttpServletRequest("GET", "/api/x"),
            response,
            new BadCredentialsException("user alice failed"));

    assertThat(response.getContentAsString()).doesNotContain("alice").doesNotContain("failed");
  }

  @Test
  void escapesTheInstancePathSoTheBodyIsAlwaysValidJson() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/a\"b\\c");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ProblemDetailAuthenticationEntryPoint()
        .commence(request, response, new BadCredentialsException("x"));

    assertThat(response.getContentAsString()).contains("\"instance\":\"/api/a\\\"b\\\\c\"");
  }
}
