package com.chm.myfinances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountHasHistoryException;
import com.chm.myfinances.application.category.CategoryNameAlreadyExistsException;
import com.chm.myfinances.application.transaction.AccountTypeNotAllowedException;
import com.chm.myfinances.application.transfer.AccountClosedException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

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

  // --- F032: bean-validation violation-code derivation ---------------------------------------
  //
  // Spring's SpringValidatorAdapter resolves a FieldError's/ObjectError's own getCode() to the
  // *last* element of its message-codes array, which is always the bare constraint annotation's
  // simple name (e.g. "NotBlank", "PositiveOrZero") with no object/field qualification - verified
  // empirically against a real LocalValidatorFactoryBean run (see GlobalExceptionHandlerTest for
  // the real-REST-call coverage); hand-constructing FieldError/ObjectError with that one-element
  // codes array reproduces the exact same getCode() value without needing a validator here.

  @Test
  void codeForFieldErrorKebabCasesTheConstraintAnnotationSimpleName() {
    assertThat(ProblemTypeSlugs.codeFor(fieldError("description", "NotBlank")))
        .isEqualTo("not-blank");
  }

  @Test
  void codeForFieldErrorHandlesMultiWordConstraintNames() {
    assertThat(ProblemTypeSlugs.codeFor(fieldError("amount", "PositiveOrZero")))
        .isEqualTo("positive-or-zero");
    assertThat(ProblemTypeSlugs.codeFor(fieldError("targetPercentage", "DecimalMin")))
        .isEqualTo("decimal-min");
    assertThat(ProblemTypeSlugs.codeFor(fieldError("targetPercentage", "DecimalMax")))
        .isEqualTo("decimal-max");
    assertThat(ProblemTypeSlugs.codeFor(fieldError("entries", "NotEmpty"))).isEqualTo("not-empty");
    assertThat(ProblemTypeSlugs.codeFor(fieldError("liters", "Digits"))).isEqualTo("digits");
  }

  @Test
  void isAssertTrueViolationDetectsTheAssertTrueConstraintCodeOnly() {
    assertThat(
            ProblemTypeSlugs.isAssertTrueViolation(
                fieldError("atMostOneResultingBalancePerProduct", "AssertTrue")))
        .isTrue();
    assertThat(ProblemTypeSlugs.isAssertTrueViolation(fieldError("amount", "Positive"))).isFalse();
  }

  @Test
  void codeForAssertTrueDerivesFromTheSyntheticPropertyNameNotTheLiteralConstraintName() {
    // @AssertTrue (JSR-380) can only target a method/field, never a class/record directly, so this
    // codebase's class-level invariants are each written as an isXxx() getter
    // (TradeConfirmationRequest.isAtMostOneResultingBalancePerProduct,
    // TradeConfirmationLineRequest.isCloseHoldingOnlyOnSell, ...). Hibernate Validator treats that
    // as a bean property named "xxx", so Spring's SpringValidatorAdapter reports the violation as
    // a FieldError whose field is that synthetic property path - never a real request field, and
    // never a global/object error with an empty path, as a naive reading of "class-level
    // constraint" would suggest. The literal constraint code ("AssertTrue") is identical for every
    // such invariant in the app, so the synthetic property name is the only part that
    // distinguishes one invariant from another - that's what this method derives the code from.
    assertThat(
            ProblemTypeSlugs.codeForAssertTrue(
                fieldError("atMostOneResultingBalancePerProduct", "AssertTrue")))
        .isEqualTo("at-most-one-resulting-balance-per-product");

    // Nested inside a @Valid-cascaded list (TradeConfirmationRequest.lines[0]), the synthetic
    // property name is the path's last segment, after the index - only that segment names the
    // invariant.
    assertThat(
            ProblemTypeSlugs.codeForAssertTrue(
                fieldError("lines[0].closeHoldingOnlyOnSell", "AssertTrue")))
        .isEqualTo("close-holding-only-on-sell");
  }

  @Test
  void codeForObjectErrorKebabCasesItsCodeTheSameWayAsAFieldError() {
    ObjectError objectError =
        new ObjectError(
            "tradeConfirmationRequest", new String[] {"SomeClassLevelCheck"}, null, "msg");

    assertThat(ProblemTypeSlugs.codeFor(objectError)).isEqualTo("some-class-level-check");
  }

  private static FieldError fieldError(String field, String constraintSimpleName) {
    return new FieldError(
        "request", field, null, false, new String[] {constraintSimpleName}, null, "msg");
  }
}
