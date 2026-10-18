package com.chm.myfinances.testsupport.fakes;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The in-memory fakes' stand-in for the Postgres {@code lower(unaccent(column)) LIKE
 * lower(unaccent(pattern)) ESCAPE '\'} clause (F037, ADR 0030): a case-insensitive substring match
 * after stripping combining marks with {@link Normalizer}. A literal {@code %}, {@code _} or {@code
 * \} in the search text matches itself, as the real adapter's escaping guarantees. The fold mirrors
 * {@code unaccent} for decomposable letters only (not, for example, {@code ß}); the real behavior
 * is asserted by the repository integration tests.
 */
final class FakeTextSearch {

  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  private FakeTextSearch() {}

  /** Whether {@code text} (may be {@code null}) contains {@code searchText}, folded. */
  static boolean contains(String text, String searchText) {
    if (text == null) {
      return false;
    }
    return fold(text).contains(fold(searchText.trim()));
  }

  private static String fold(String value) {
    String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
    return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }
}
