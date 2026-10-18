package com.chm.myfinances.infrastructure.persistence.shared;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * The accent- and case-insensitive text-search predicate shared by the transaction and transfer
 * adapters (F037, ADR 0030): {@code lower(unaccent(column)) LIKE lower(unaccent(pattern)) ESCAPE
 * '\'}. It needs the Postgres {@code unaccent} extension (migration V25); the call is unqualified,
 * so it resolves through the connection's {@code search_path}.
 */
public final class TextSearchPredicates {

  private static final char ESCAPE = '\\';

  private TextSearchPredicates() {}

  /**
   * The {@code LIKE} pattern for "contains {@code text}": {@code \}, {@code %} and {@code _} in the
   * text are escaped with {@code \} (the {@code LIKE} escape character) so they match literally,
   * then the whole is wrapped in {@code %...%}. {@code CriteriaBuilder.like(expr, pattern,
   * escapeChar)} only declares the escape character, it does not escape the user's text, so this is
   * done here.
   */
  public static String likePattern(String text) {
    StringBuilder pattern = new StringBuilder(text.length() + 2).append('%');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ESCAPE || c == '%' || c == '_') {
        pattern.append(ESCAPE);
      }
      pattern.append(c);
    }
    return pattern.append('%').toString();
  }

  /**
   * Whether {@code column} contains the already-built {@code pattern} (see {@link #likePattern}),
   * ignoring case and accents. A {@code null} column value never matches (SQL {@code NULL LIKE ...}
   * is not true).
   */
  public static Predicate matches(
      CriteriaBuilder criteriaBuilder, Expression<String> column, String pattern) {
    Expression<String> foldedColumn =
        criteriaBuilder.lower(criteriaBuilder.function("unaccent", String.class, column));
    Expression<String> foldedPattern =
        criteriaBuilder.lower(
            criteriaBuilder.function("unaccent", String.class, criteriaBuilder.literal(pattern)));
    return criteriaBuilder.like(foldedColumn, foldedPattern, ESCAPE);
  }
}
