package com.chm.myfinances.infrastructure.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Derives the RFC 9457 {@code type}/{@code title} every error response carries (ADR 0026, F031)
 * from an exception's simple class name - no per-exception authoring, since the class name is
 * already the identifier {@link GlobalExceptionHandler} logs at INFO as safe (ADR 0011).
 *
 * <p>Classes duplicated across aggregate packages only for the no-cross-aggregate-import ArchUnit
 * rule (ADR 0014) - e.g. {@code AccountClosedException} in {@code application.transfer}, {@code
 * application.transaction} and {@code application.recurringtemplate} - collapse to the same slug on
 * purpose: the duplication is a code-organization artifact, not a difference in the real-world
 * condition.
 *
 * <p>F032 reuses the same kebab-casing transform for a second, unrelated identifier: a bean-
 * validation violation's {@code code} (e.g. {@code @NotBlank} -> {@code not-blank}), attached by
 * {@link GlobalExceptionHandler} as the {@code errors} extension member of a {@code
 * MethodArgumentNotValidException}'s {@link org.springframework.http.ProblemDetail}.
 */
public final class ProblemTypeSlugs {

  private static final String EXCEPTION_SUFFIX = "Exception";

  /** The literal {@code getCode()} every {@code @AssertTrue} violation carries (see below). */
  private static final String ASSERT_TRUE_CODE = "AssertTrue";

  /** Fixed slug for the generic unexpected-exception (500) case - never derived from a class. */
  public static final URI INTERNAL_ERROR_TYPE = URI.create("urn:my-finances:error:internal-error");

  /** Fixed title paired with {@link #INTERNAL_ERROR_TYPE}. */
  public static final String INTERNAL_ERROR_TITLE = "An unexpected error occurred";

  private ProblemTypeSlugs() {}

  /**
   * Simple name, {@code Exception} suffix stripped, kebab-cased (a hyphen inserted before each
   * internal - i.e. not the first character - uppercase letter), lowercased. {@code
   * AccountHasHistoryException} -> {@code account-has-history}.
   */
  public static String slugFor(Class<?> exceptionClass) {
    String simpleName = exceptionClass.getSimpleName();
    String withoutSuffix =
        simpleName.endsWith(EXCEPTION_SUFFIX)
            ? simpleName.substring(0, simpleName.length() - EXCEPTION_SUFFIX.length())
            : simpleName;
    return kebabCase(withoutSuffix);
  }

  /** {@code urn:my-finances:error:<slugFor(exceptionClass)>}. */
  public static URI typeUriFor(Class<?> exceptionClass) {
    return URI.create("urn:my-finances:error:" + slugFor(exceptionClass));
  }

  /**
   * Humanized slug with the first letter capitalized. {@code account-has-history} -> {@code
   * "Account has history"}.
   */
  public static String titleFor(Class<?> exceptionClass) {
    String humanized = slugFor(exceptionClass).replace('-', ' ');
    return humanized.substring(0, 1).toUpperCase(Locale.ROOT) + humanized.substring(1);
  }

  /**
   * F032: a field-level bean-validation violation's {@code code}, derived from the constraint
   * annotation's own simple name - {@code fieldError.getCode()} already resolves to exactly that
   * (Spring's {@code SpringValidatorAdapter} puts the bare annotation name, e.g. {@code
   * "NotBlank"}/{@code "PositiveOrZero"}, as the last and therefore "default" element of the
   * violation's message-codes array), kebab-cased the same way an exception class name is: {@code
   * NotBlank} -> {@code not-blank}, {@code PositiveOrZero} -> {@code positive-or-zero}.
   *
   * <p>Never call this for an {@code @AssertTrue} violation ({@link #isAssertTrueViolation}) -
   * every such violation in this codebase shares the one literal code {@code "AssertTrue"}, which
   * would collapse every cross-field invariant in the app to the same uninformative {@code
   * assert-true} code; use {@link #codeForAssertTrue} instead.
   */
  public static String codeFor(FieldError fieldError) {
    return kebabCase(fieldError.getCode());
  }

  /**
   * F032: an object-level (global) bean-validation violation's {@code code}, kebab-cased the same
   * way as {@link #codeFor(FieldError)}. No class-level constraint in this codebase currently
   * produces a global error (see {@link #isAssertTrueViolation}'s javadoc), but {@link
   * GlobalExceptionHandler} still walks {@code BindingResult.getGlobalErrors()} for forward
   * compatibility with any future genuine class-level constraint.
   */
  public static String codeFor(ObjectError objectError) {
    return kebabCase(objectError.getCode());
  }

  /**
   * True when {@code fieldError} comes from an {@code @AssertTrue} constraint - this codebase's
   * only class-level-invariant mechanism ({@code TradeConfirmationRequest.
   * isAtMostOneResultingBalancePerProduct}, {@code TradeConfirmationLineRequest.
   * isCloseHoldingOnlyOnSell}, and others). JSR-380's {@code @AssertTrue} can only target a
   * method/field, never a class/record directly, so each of these invariants is written as an
   * {@code isXxx()} getter; Hibernate Validator then treats {@code xxx} as an ordinary bean
   * property, and Spring's {@code SpringValidatorAdapter} reports the violation as a {@link
   * FieldError} whose field is that synthetic property path - verified empirically against a real
   * {@code LocalValidatorFactoryBean} run, not a global {@link ObjectError} with an empty path as a
   * literal reading of "class-level constraint" would suggest.
   */
  public static boolean isAssertTrueViolation(FieldError fieldError) {
    return ASSERT_TRUE_CODE.equals(fieldError.getCode());
  }

  /**
   * F032: the {@code code} for an {@code @AssertTrue} violation ({@link #isAssertTrueViolation}),
   * derived from its synthetic property name instead of the literal constraint code (every such
   * violation's {@code getCode()} is the same bare {@code "AssertTrue"} string, which carries no
   * information about *which* invariant failed). The property name is the field path's last segment
   * - for a top-level invariant that is the whole path ({@code
   * atMostOneResultingBalancePerProduct}); nested inside a {@code @Valid}-cascaded list ({@code
   * lines[0].closeHoldingOnlyOnSell}) it's everything after the last {@code '.'}.
   */
  public static String codeForAssertTrue(FieldError fieldError) {
    String field = fieldError.getField();
    String propertyName = field.substring(field.lastIndexOf('.') + 1);
    return kebabCase(propertyName);
  }

  /**
   * A hyphen inserted before each internal (not the first character) uppercase letter, then
   * lowercased - shared by every slug/code this class derives, whether from an exception class's
   * simple name or a constraint annotation's/bean property's simple name.
   */
  private static String kebabCase(String pascalOrCamelCase) {
    return pascalOrCamelCase.replaceAll("(?<!^)(?=[A-Z])", "-").toLowerCase(Locale.ROOT);
  }
}
