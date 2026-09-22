package com.chm.myfinances;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Pins a rule that otherwise lives only in prose in {@code backend/CLAUDE.md} (issue #31, B15):
 * nothing in {@code domain..} logs. Scans only main sources ({@link
 * ImportOption.DoNotIncludeTests}) - this rule is about production code, not the test tree.
 *
 * <p>The other prose rule this issue asked for - "domain packages never import another aggregate's
 * package" - is deliberately NOT here: writing it as an ArchUnit rule surfaced a real, existing
 * violation ({@code domain.transaction.Transaction} depends on {@code domain.category.CategoryType}
 * - see the class's own javadoc: {@code type} is intentionally denormalized from the category's, "a
 * stable, redundant data-integrity check... even though Category.type can never itself change").
 * Per issue #31's instructions, a rule that fails against the current codebase is reported, not
 * silently fixed or weakened, so it is left out of this PR pending a decision on either an
 * exception/refactor or a documented amendment to the rule's wording.
 */
@AnalyzeClasses(
    packages = "com.chm.myfinances",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String DOMAIN_PACKAGE = "com.chm.myfinances.domain";

  /**
   * "Never log in domain/" (root {@code CLAUDE.md}, Logging). Checked as "no dependency on {@code
   * org.slf4j..}" rather than only forbidding the {@code @Slf4j} annotation directly, because
   * Lombok's {@code @Slf4j} lowers, at compile time, to a field of type {@code org.slf4j.Logger}
   * initialized via {@code org.slf4j.LoggerFactory} - so this one dependency check catches both a
   * hand-written {@code Logger} field and {@code @Slf4j}.
   */
  @ArchTest
  static final ArchRule domainClassesDoNotLog =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN_PACKAGE + "..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.slf4j..");
}
