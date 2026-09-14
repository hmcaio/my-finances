package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.category.CategoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer test for {@link GlobalExceptionHandler}, the cross-cutting
 * {@code @RestControllerAdvice} that guarantees unexpected/unhandled exceptions never leak their
 * message, class name, or stack trace into the HTTP response (security-audit fix). Boots the real
 * app context against Testcontainers Postgres (ADR 0010) — see {@code CategoryControllerTest} for
 * why {@link MockMvc} is built by hand here instead of via {@code @AutoConfigureMockMvc} (removed
 * in Boot 4.x) — and overrides just the one collaborator ({@link CategoryRepository}) needed to
 * force a realistic unchecked exception, exactly as a real bug (e.g. a database error) would
 * surface it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class GlobalExceptionHandlerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  @MockitoBean private CategoryRepository categoryRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void unexpectedExceptionReturnsGenericBodyWithNoLeakedDetails() throws Exception {
    // A realistic unchecked failure a repository could throw (e.g. a database driver error) -
    // deliberately carries a message that would be a real leak if it reached the client.
    given(categoryRepository.findAll())
        .willThrow(new RuntimeException("sensitive detail: connection refused at 10.0.0.5:5432"));

    MvcResult result =
        mockMvc
            .perform(get("/api/categories"))
            .andExpect(status().isInternalServerError())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .contains("An unexpected error occurred")
        .doesNotContain("sensitive detail")
        .doesNotContain("10.0.0.5")
        .doesNotContain("RuntimeException")
        .doesNotContain("com.chm.myfinances")
        .doesNotContain("\tat "); // no stack trace frame lines
  }

  @Test
  void notFoundExceptionIsUnaffectedByTheGlobalHandlerAndStillReturns404() throws Exception {
    // No stubbing: CategoryRepository#existsById on an un-stubbed mock defaults to false, so the
    // real CategoryService/CategoryController path throws CategoryNotFoundException exactly as it
    // would for a genuinely missing id - proving the new catch-all handler does not shadow the
    // existing @ResponseStatus(HttpStatus.NOT_FOUND) mapping.
    mockMvc
        .perform(delete("/api/categories/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unmappedRouteStillReturnsPlain404UnaffectedByTheGlobalHandler() throws Exception {
    // A request to a route no controller maps triggers Spring's own NoResourceFoundException,
    // which reports its 404 programmatically (via the ErrorResponse contract) rather than through
    // a @ResponseStatus annotation on its class. Since GlobalExceptionHandler's @ResponseStatus
    // check wouldn't match this exception, this proves the catch-all doesn't downgrade it to a
    // generic 500.
    mockMvc.perform(get("/api/this-route-does-not-exist")).andExpect(status().isNotFound());
  }
}
