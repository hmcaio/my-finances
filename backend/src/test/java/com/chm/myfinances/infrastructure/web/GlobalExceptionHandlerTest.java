package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.LogCapture;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * REST-layer test for {@link GlobalExceptionHandler} (ADR 0026, F031): every error response - this
 * app's own {@code @ResponseStatus} exceptions, Spring's own {@link ErrorResponse} exceptions,
 * {@link HttpMessageNotReadableException}/ {@link TypeMismatchException}, and the generic
 * unexpected-exception fallback - now returns an RFC 9457 {@link ProblemDetail} body built by the
 * handler itself (no more rethrow), while the original security-audit guarantee (no leaked
 * message/class name/stack trace) and the F016 logging behavior stay exactly as before. Boots the
 * real app context against Testcontainers Postgres (ADR 0010) — see {@code CategoryControllerTest}
 * for why {@link MockMvc} is built by hand here instead of via {@code @AutoConfigureMockMvc}
 * (removed in Boot 4.x) — and overrides just the one collaborator ({@link CategoryRepository})
 * needed to force a realistic unchecked exception, exactly as a real bug (e.g. a database error)
 * would surface it.
 */
@Tag("integration")
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
  void unexpectedExceptionReturnsAProblemDetailBodyWithNoLeakedDetails() throws Exception {
    // A realistic unchecked failure a repository could throw (e.g. a database driver error) -
    // deliberately carries a message that would be a real leak if it reached the client.
    given(categoryRepository.findAll())
        .willThrow(new RuntimeException("sensitive detail: connection refused at 10.0.0.5:5432"));

    MvcResult result =
        mockMvc
            .perform(get("/api/categories"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.type").value("urn:my-finances:error:internal-error"))
            .andExpect(jsonPath("$.title").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.instance").value("/api/categories"))
            .andExpect(jsonPath("$.detail").doesNotExist())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("sensitive detail")
        .doesNotContain("10.0.0.5")
        .doesNotContain("RuntimeException")
        .doesNotContain("com.chm.myfinances")
        .doesNotContain("\tat "); // no stack trace frame lines
  }

  @Test
  void notFoundResponseStatusExceptionReturnsADerivedTypeAndStillA404() throws Exception {
    // No stubbing: CategoryRepository#existsById on an un-stubbed mock defaults to false, so the
    // real CategoryService/CategoryController path throws CategoryNotFoundException exactly as it
    // would for a genuinely missing id - proving the new catch-all handler does not shadow the
    // existing @ResponseStatus(HttpStatus.NOT_FOUND) mapping, and now also builds a ProblemDetail
    // body for it instead of an empty one.
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/categories/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:category-not-found"))
        .andExpect(jsonPath("$.title").value("Category not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.instance").value("/api/categories/" + id))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void conflictResponseStatusExceptionReturnsADerivedTypeAndStillA409() throws Exception {
    // CategoryController.delete against a built-in category throws the @ResponseStatus(CONFLICT)
    // BuiltInCategoryException - a second, representative @ResponseStatus status code alongside
    // the 404 case above. categoryRepository is @MockitoBean-replaced for this whole test class, so
    // stub findById to hand CategoryService a built-in Category rather than relying on seeded rows.
    UUID id = UUID.randomUUID();
    Category builtIn =
        Category.reconstitute(id, "Other Expense", CategoryType.EXPENSE, true, false);
    given(categoryRepository.findById(id)).willReturn(Optional.of(builtIn));

    mockMvc
        .perform(delete("/api/categories/" + id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:built-in-category"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void unmappedRouteReturnsAProblemDetailBodyForSpringsOwnErrorResponseExceptionAndStillA404()
      throws Exception {
    // A request to a route no controller maps triggers Spring's own NoResourceFoundException,
    // which reports its 404 programmatically (via the ErrorResponse contract) rather than through
    // a @ResponseStatus annotation on its class. Proves the handler now builds a ProblemDetail body
    // for this case too (it used to rethrow to Spring's default resolver, which sent none).
    mockMvc
        .perform(get("/api/this-route-does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:no-resource-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void unexpectedExceptionIsLoggedOnceAtErrorWithTheThrowableAndTheBodyStaysAProblemDetail()
      throws Exception {
    RuntimeException failure = new RuntimeException("sensitive detail: row 42 failed");
    given(categoryRepository.findAll()).willThrow(failure);

    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      mockMvc
          .perform(get("/api/categories"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.type").value("urn:my-finances:error:internal-error"))
          .andExpect(jsonPath("$.title").value("An unexpected error occurred"))
          .andExpect(jsonPath("$.detail").doesNotExist());

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
  void unreadableMessageReturnsBadRequestWithADerivedTypeAndIsNotLoggedAtError() {
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("bad body", new MockHttpInputMessage(new byte[0]));

    assertBadRequestProblemDetailWithoutErrorLog(
        exception, "urn:my-finances:error:http-message-not-readable");
  }

  @Test
  void typeMismatchReturnsBadRequestWithADerivedTypeAndIsNotLoggedAtError() {
    TypeMismatchException exception = new TypeMismatchException("not-a-uuid", UUID.class);

    assertBadRequestProblemDetailWithoutErrorLog(exception, "urn:my-finances:error:type-mismatch");
  }

  @Test
  void methodArgumentTypeMismatchReturnsBadRequestWithADerivedTypeAndIsNotLoggedAtError() {
    MethodArgumentTypeMismatchException exception =
        new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null, null);

    assertBadRequestProblemDetailWithoutErrorLog(
        exception, "urn:my-finances:error:method-argument-type-mismatch");
  }

  private static void assertBadRequestProblemDetailWithoutErrorLog(
      Exception exception, String expectedType) {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");

    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      ResponseEntity<ProblemDetail> response = handler.handleUnexpected(exception, request);

      assertThat(response.getStatusCode().value()).isEqualTo(400);
      ProblemDetail body = response.getBody();
      assertThat(body).isNotNull();
      assertThat(body.getType()).isEqualTo(URI.create(expectedType));
      assertThat(body.getDetail()).isNull();
      assertThat(body.getInstance()).isEqualTo(URI.create("/api/anything"));

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
  void aMalformedJsonBodyReturns400WithADerivedTypeAndNoLeakedDetails(String body)
      throws Exception {
    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
              .andExpect(status().isBadRequest())
              .andExpect(
                  jsonPath("$.type").value("urn:my-finances:error:http-message-not-readable"))
              .andExpect(jsonPath("$.detail").doesNotExist())
              .andReturn();

      assertThat(result.getResponse().getContentAsString()).doesNotContain("BOGUS");
      assertNoLeakedExceptionDetails(result);
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
  void anUnparseableIdOrDateInThePathOrQueryReturns400WithADerivedTypeAndNoLeakedDetails(String uri)
      throws Exception {
    try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
      // `instance` echoes the request's own path/query (same convention as the access log) - that
      // is not a leak, since the client already knows what it requested. Only the exception's
      // message/class/stack trace must never appear.
      MvcResult result =
          mockMvc
              .perform(get(uri))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.detail").doesNotExist())
              .andReturn();

      assertThat(result.getResponse().getContentAsString())
          .contains("urn:my-finances:error:")
          .contains("method-argument-type-mismatch");
      assertNoLeakedExceptionDetails(result);
      assertThat(logs.eventsAt(Level.ERROR)).isEmpty();
    }
  }

  private static void assertNoLeakedExceptionDetails(MvcResult result) throws Exception {
    assertThat(result.getResponse().getContentAsString())
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
