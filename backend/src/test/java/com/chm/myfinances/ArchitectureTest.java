package com.chm.myfinances;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Pins two rules that otherwise live only in prose in {@code backend/CLAUDE.md} (issue #31, B15):
 * nothing in {@code domain..} logs, and no aggregate's {@code domain} package imports another
 * aggregate's {@code domain} package. Scans only main sources ({@link
 * ImportOption.DoNotIncludeTests}) - these rules are about production code, not the test tree.
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

  /**
   * "Domain packages never import another aggregate's package" (root {@code CLAUDE.md}, Layout and
   * shapes), with the one documented exception that rule's own wording now carries: {@link
   * Transaction} depends on {@link CategoryType}. See {@code Transaction}'s own javadoc for why -
   * {@code type} is captured at creation, denormalized from the category's own (already-immutable)
   * type, so a stable, redundant data-integrity check is available even though {@code
   * Category.type} can never itself change.
   *
   * <p>Classes in {@code domain.shared} are not "another aggregate" - every aggregate is expected
   * to depend on the shared kernel ({@code TextFieldConstraints}, {@code AuditableEntity}, ...) -
   * so they're excluded from both sides of the check. Everything else is compared by the first
   * package segment under {@code domain} (e.g. {@code domain.transaction} vs. {@code
   * domain.category}); a class depending on another class in its own aggregate's package is not a
   * violation.
   *
   * <p>This can't be expressed as a single static {@code resideInAnyPackage(...)} target, because
   * "another aggregate" is relative to each origin class's own package, not a fixed list - so it's
   * a custom {@link ArchCondition} that inspects each class's direct dependencies itself, with the
   * {@code Transaction} -&gt; {@code CategoryType} edge excluded by name and nothing else.
   */
  @ArchTest
  static final ArchRule domainAggregatesDoNotDependOnEachOther =
      classes()
          .that()
          .resideInAPackage(DOMAIN_PACKAGE + "..")
          .should(notDependOnAnotherAggregatesDomainPackage());

  private static ArchCondition<JavaClass> notDependOnAnotherAggregatesDomainPackage() {
    return new ArchCondition<>("not depend on another aggregate's domain package") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        String originAggregate = aggregateOf(origin);
        if (originAggregate == null) {
          return;
        }
        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
          JavaClass target = dependency.getTargetClass();
          String targetAggregate = aggregateOf(target);
          if (targetAggregate == null || targetAggregate.equals(originAggregate)) {
            continue;
          }
          if (origin.isEquivalentTo(Transaction.class)
              && target.isEquivalentTo(CategoryType.class)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  origin,
                  String.format(
                      "%s depends on %s (aggregate '%s' imports aggregate '%s')",
                      origin.getFullName(),
                      target.getFullName(),
                      originAggregate,
                      targetAggregate)));
        }
      }
    };
  }

  /**
   * The first package segment under {@code domain} (e.g. {@code "transaction"} for {@code
   * com.chm.myfinances.domain.transaction.Transaction}), or {@code null} for classes directly in
   * {@code domain} itself or in {@code domain.shared} - neither is "another aggregate".
   */
  private static String aggregateOf(JavaClass javaClass) {
    String packageName = javaClass.getPackageName();
    String prefix = DOMAIN_PACKAGE + ".";
    if (!packageName.startsWith(prefix)) {
      return null;
    }
    String remainder = packageName.substring(prefix.length());
    int dotIndex = remainder.indexOf('.');
    String firstSegment = dotIndex == -1 ? remainder : remainder.substring(0, dotIndex);
    return "shared".equals(firstSegment) ? null : firstSegment;
  }
}
