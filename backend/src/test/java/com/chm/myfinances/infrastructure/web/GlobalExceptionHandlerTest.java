package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.testsupport.LogCapture;
import com.chm.myfinances.testsupport.MockMvcSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

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
    mockMvc = MockMvcSupport.build(webApplicationContext);
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

  @Test
  void unreadableMessageIsRethrownNotConvertedToTheGeneric500AndNotLoggedAtError() {
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("bad body", new MockHttpInputMessage(new byte[0]));

    assertRethrownWithoutErrorLog(exception);
  }

  @Test
  void typeMismatchIsRethrownNotConvertedToTheGeneric500AndNotLoggedAtError() {
    TypeMismatchException exception = new TypeMismatchException("not-a-uuid", UUID.class);

    assertRethrownWithoutErrorLog(exception);
  }

  @Test
  void methodArgumentTypeMismatchIsRethrownNotConvertedToTheGeneric500AndNotLoggedAtError() {
    MethodArgumentTypeMismatchException exception =
        new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null, null);

    assertRethrownWithoutErrorLog(exception);
  }

  private static void assertRethrownWithoutErrorLog(Exception exception) {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");

    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      assertThatThrownBy(() -> handler.handleUnexpected(exception, request)).isSameAs(exception);

      assertThat(logs.eventsAt(Level.ERROR)).isEmpty();
      assertThat(logs.events()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{not json",
        "",
        "{\"name\":\"x\",\"type\":\"BOGUS\"}",
        "{\"name\":[\"x\"],\"type\":\"EXPENSE\"}"
      })
  void aMalformedJsonBodyReturns400WithNoLeakedDetails(String body) throws Exception {
    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
              .andExpect(status().isBadRequest())
              .andReturn();

      assertNoLeakedDetails(result);
      assertThat(logs.eventsAt(Level.ERROR)).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/transactions/not-a-uuid",
        "/api/accounts/not-a-uuid",
        "/api/transactions?accountId=garbage",
        "/api/transactions?dateFrom=garbage"
      })
  void anUnparseableIdOrDateInThePathOrQueryReturns400WithNoLeakedDetails(String uri)
      throws Exception {
    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      MvcResult result = mockMvc.perform(get(uri)).andExpect(status().isBadRequest()).andReturn();

      assertNoLeakedDetails(result);
      assertThat(logs.eventsAt(Level.ERROR)).isEmpty();
    }
  }

  private static void assertNoLeakedDetails(MvcResult result) throws Exception {
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("An unexpected error occurred")
        .doesNotContain("garbage")
        .doesNotContain("not-a-uuid")
        .doesNotContain("BOGUS")
        .doesNotContain("Exception")
        .doesNotContain("com.chm.myfinances")
        .doesNotContain("\tat ");
  }

  @Test
  void aValidationFailureNeverLogsTheRejectedValues() throws Exception {
    // DefaultHandlerExceptionResolver always warn-logs "Resolved [MethodArgumentNotValidException:
    // ...]" itself (spring.mvc.log-resolved-exception doesn't cover it), and that message includes
    // "rejected value [-12.34]" - an amount - so application.yml silences its logger.
    String body =
        """
        {"date":"2026-01-01","amount":-12.34,"description":"leaky description",
         "accountId":"%1$s","categoryId":"%1$s"}
        """
            .formatted(UUID.randomUUID());

    try (LogCapture logs = LogCapture.ofConfiguredLevel(DefaultHandlerExceptionResolver.class)) {
      mockMvc
          .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());

      assertThat(logs.events()).isEmpty();
    }
  }
}
