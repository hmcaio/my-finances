package com.chm.myfinances.infrastructure.web;

import java.net.URI;
import java.util.Locale;

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
 */
public final class ProblemTypeSlugs {

  private static final String EXCEPTION_SUFFIX = "Exception";

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
    return withoutSuffix.replaceAll("(?<!^)(?=[A-Z])", "-").toLowerCase(Locale.ROOT);
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
}
