package com.chm.myfinances.infrastructure.persistence.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link TextSearchPredicates#likePattern} (F037, ADR 0030), written first (ADR 0004): the user's
 * text is escaped in Java so {@code %}, {@code _} and the escape character itself match literally,
 * then wrapped in {@code %...%}. The predicate itself, which needs Postgres, is covered by the
 * repository integration tests.
 */
class TextSearchPredicatesTest {

  @Test
  void wrapsPlainTextInPercentSigns() {
    assertThat(TextSearchPredicates.likePattern("cafe")).isEqualTo("%cafe%");
  }

  @Test
  void escapesPercentUnderscoreAndBackslashWithABackslash() {
    assertThat(TextSearchPredicates.likePattern("50%")).isEqualTo("%50\\%%");
    assertThat(TextSearchPredicates.likePattern("a_b")).isEqualTo("%a\\_b%");
    assertThat(TextSearchPredicates.likePattern("c:\\dir")).isEqualTo("%c:\\\\dir%");
    assertThat(TextSearchPredicates.likePattern("%_\\")).isEqualTo("%\\%\\_\\\\%");
  }

  @Test
  void aLoneWildcardCharacterDoesNotBecomeAMatchEverythingPattern() {
    assertThat(TextSearchPredicates.likePattern("%")).isEqualTo("%\\%%");
    assertThat(TextSearchPredicates.likePattern("_")).isEqualTo("%\\_%");
  }

  @Test
  void leavesAccentedLettersAloneBecauseFoldingHappensInTheDatabase() {
    assertThat(TextSearchPredicates.likePattern("Café")).isEqualTo("%Café%");
  }
}
