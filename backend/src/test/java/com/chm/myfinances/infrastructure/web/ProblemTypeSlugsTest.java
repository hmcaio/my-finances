package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountHasHistoryException;
import com.chm.myfinances.application.category.CategoryNameAlreadyExistsException;
import com.chm.myfinances.application.transaction.AccountTypeNotAllowedException;
import com.chm.myfinances.application.transfer.AccountClosedException;
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the slug-derivation rules behind every {@code type}/{@code title} the {@link
 * GlobalExceptionHandler} emits (ADR 0026, F031): the exception's simple class name, {@code
 * Exception} suffix stripped, kebab-cased and namespaced as a URN. No Spring context needed - this
 * is pure string manipulation over {@code Class} literals.
 */
class ProblemTypeSlugsTest {

  @Test
  void slugForStripsExceptionSuffixAndKebabCasesTheSimpleName() {
    assertThat(ProblemTypeSlugs.slugFor(AccountHasHistoryException.class))
        .isEqualTo("account-has-history");
  }

  @Test
  void typeUriForNamespacesTheSlugAsAMyFinancesErrorUrn() {
    assertThat(ProblemTypeSlugs.typeUriFor(AccountHasHistoryException.class))
        .isEqualTo(URI.create("urn:my-finances:error:account-has-history"));
  }

  @Test
  void slugForHandlesMultiWordClassNames() {
    assertThat(ProblemTypeSlugs.slugFor(CategoryNameAlreadyExistsException.class))
        .isEqualTo("category-name-already-exists");
  }

  @Test
  void slugForCollapsesClassesDuplicatedAcrossAggregatePackagesToTheSameSlug() {
    // AccountClosedException exists separately in application.transfer, application.transaction
    // and application.recurringtemplate only because of the no-cross-aggregate-import ArchUnit
    // rule (ADR 0014) - same real-world condition, so the same slug is correct (ADR 0026). All
    // three share a simple name, so only one can be imported; the other two stay fully qualified.
    String transferSlug = ProblemTypeSlugs.slugFor(AccountClosedException.class);
    String transactionSlug =
        ProblemTypeSlugs.slugFor(
            com.chm.myfinances.application.transaction.AccountClosedException.class);
    String recurringTemplateSlug =
        ProblemTypeSlugs.slugFor(
            com.chm.myfinances.application.recurringtemplate.AccountClosedException.class);

    assertThat(transferSlug)
        .isEqualTo("account-closed")
        .isEqualTo(transactionSlug)
        .isEqualTo(recurringTemplateSlug);
  }

  @Test
  void slugForCollapsesAccountTypeNotAllowedAcrossItsTwoPackagesToo() {
    String transactionSlug = ProblemTypeSlugs.slugFor(AccountTypeNotAllowedException.class);
    String recurringTemplateSlug =
        ProblemTypeSlugs.slugFor(
            com.chm.myfinances.application.recurringtemplate.AccountTypeNotAllowedException.class);

    assertThat(transactionSlug)
        .isEqualTo("account-type-not-allowed")
        .isEqualTo(recurringTemplateSlug);
  }

  @Test
  void titleForHumanizesTheSlugWithTheFirstLetterCapitalized() {
    assertThat(ProblemTypeSlugs.titleFor(AccountHasHistoryException.class))
        .isEqualTo("Account has history");
  }

  @Test
  void internalErrorConstantsAreFixedNotDerivedFromAnyExceptionClass() {
    assertThat(ProblemTypeSlugs.INTERNAL_ERROR_TYPE)
        .isEqualTo(URI.create("urn:my-finances:error:internal-error"));
    assertThat(ProblemTypeSlugs.INTERNAL_ERROR_TITLE).isEqualTo("An unexpected error occurred");
  }
}
