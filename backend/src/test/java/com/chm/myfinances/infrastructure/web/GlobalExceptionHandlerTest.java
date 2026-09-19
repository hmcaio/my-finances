package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.testsupport.LogCapture;
import java.util.List;
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

  @Test
  void unexpectedExceptionIsLoggedOnceAtErrorWithTheThrowableAndTheBodyStaysGeneric()
      throws Exception {
    RuntimeException failure = new RuntimeException("sensitive detail: row 42 failed");
    given(categoryRepository.findAll()).willThrow(failure);

    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      MvcResult result =
          mockMvc
              .perform(get("/api/categories"))
              .andExpect(status().isInternalServerError())
              .andReturn();

      List<ILoggingEvent> errors = logs.eventsAt(Level.ERROR);
      assertThat(errors).hasSize(1);
      ILoggingEvent event = errors.get(0);
      assertThat(event.getFormattedMessage())
          .isEqualTo("Unhandled exception for GET /api/categories");
      assertThat(event.getThrowableProxy()).isNotNull();
      assertThat(event.getThrowableProxy().getClassName())
          .isEqualTo(RuntimeException.class.getName());
      assertThat(event.getThrowableProxy().getMessage()).contains("sensitive detail");
      assertThat(logs.events()).hasSize(1);

      // The no-leak invariant must not regress: the response is still exactly the generic body.
      assertThat(result.getResponse().getContentAsString())
          .isEqualTo("{\"message\":\"An unexpected error occurred\"}");
    }
  }

  @Test
  void responseStatusExceptionIsLoggedAtInfoWithItsClassNameButNotItsMessage() throws Exception {
    UUID id = UUID.randomUUID();

    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      mockMvc.perform(delete("/api/categories/" + id)).andExpect(status().isNotFound());

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("CategoryNotFoundException on DELETE /api/categories/" + id);
      assertThat(logs.messagesAt(Level.INFO).get(0)).doesNotContain("Category not found");
      assertThat(logs.eventsAt(Level.ERROR)).isEmpty();
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().get(0).getThrowableProxy()).isNull();
    }
  }

  @Test
  void springErrorResponseExceptionIsNotLoggedByTheHandler() throws Exception {
    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      mockMvc.perform(get("/api/this-route-does-not-exist")).andExpect(status().isNotFound());

      assertThat(logs.events()).isEmpty();
    }
  }
}
