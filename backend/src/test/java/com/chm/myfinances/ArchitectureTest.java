package com.chm.myfinances;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.containAnyMethodsThat;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethod;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;

import com.chm.myfinances.application.auditlog.AuditLog;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.infrastructure.config.RandomUuidGenerator;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import com.chm.myfinances.infrastructure.web.GlobalExceptionHandler;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Pins the backend's mechanical architecture rules, which are therefore not restated in prose in
 * {@code backend/CLAUDE.md} (issue #31, B15; overview in {@code
 * docs/conventions/backend-structure.md}): domain never logs and never imports another aggregate's
 * package (with one documented exception), a single {@code @RestControllerAdvice} handles
 * exceptions, new error cases never add another {@code @ExceptionHandler}, ids only ever come from
 * {@code IdGenerator} and never from {@code @GeneratedValue}, every JPA entity extends {@code
 * AuditableEntity}, {@code JpaRepository} interfaces stay package-private, a controller's handler
 * method names are unique (operation ids), and domain never depends on Spring/Jakarta/Lombok (with
 * one documented exception). Scans only main sources ({@link ImportOption.DoNotIncludeTests}) -
 * these rules are about production code, not the test tree.
 */
@AnalyzeClasses(
    packages = "com.chm.myfinances",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String DOMAIN_PACKAGE = "com.chm.myfinances.domain";

  /**
   * "Never log in domain/" (ADR 0011, {@code docs/conventions/logging.md}). Checked as "no
   * dependency on {@code org.slf4j..}" rather than only forbidding the {@code @Slf4j} annotation
   * directly, because Lombok's {@code @Slf4j} lowers, at compile time, to a field of type {@code
   * org.slf4j.Logger} initialized via {@code org.slf4j.LoggerFactory} - so this one dependency
   * check catches both a hand-written {@code Logger} field and {@code @Slf4j}.
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
   * "Domain packages never import another aggregate's package" (ADR 0004/0014), with one documented
   * exception: the {@code domain.transaction} package (the {@link Transaction} aggregate, plus
   * F037's {@code TransactionFilter} and {@code TransactionRepository.sumAmountByType}) depends on
   * {@link CategoryType}. See {@code Transaction}'s own javadoc for why - {@code type} is captured
   * at creation, denormalized from the category's own (already-immutable) type, so a stable,
   * redundant data-integrity check is available even though {@code Category.type} can never itself
   * change.
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

  /**
   * "One cross-cutting {@code @RestControllerAdvice} exists, {@code
   * infrastructure/web/GlobalExceptionHandler}, and there should never be another" (ADR 0026,
   * {@code docs/conventions/errors.md}). The filter-chain 401/403 writers in {@code
   * infrastructure/auth} are plain components, not advices, so they don't trip this rule.
   */
  @ArchTest
  static final ArchRule onlyOneRestControllerAdviceExists =
      classes()
          .that()
          .areAnnotatedWith(RestControllerAdvice.class)
          .should()
          .haveSimpleName(GlobalExceptionHandler.class.getSimpleName());

  /**
   * "A new expected error case gets its own {@code @ResponseStatus}-annotated exception - never a
   * new {@code @ExceptionHandler}" ({@code docs/conventions/errors.md}) - so no class other than
   * {@link GlobalExceptionHandler} declares one.
   */
  @ArchTest
  static final ArchRule onlyGlobalExceptionHandlerHandlesExceptions =
      classes()
          .that(containAnyMethodsThat(annotatedWith(ExceptionHandler.class)))
          .should()
          .haveSimpleName(GlobalExceptionHandler.class.getSimpleName());

  /**
   * "{@code domain/shared} holds the {@code IdGenerator} port (ADR 0005 - ids never come from
   * {@code @GeneratedValue} or ad hoc {@code UUID.randomUUID()})". {@link RandomUuidGenerator} is
   * the one {@code IdGenerator} implementation allowed to call {@code UUID.randomUUID()} directly;
   * everything else - domain, application and every other infrastructure class - must go through
   * the port.
   */
  @ArchTest
  static final ArchRule onlyRandomUuidGeneratorCallsUuidRandomUuid =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(RandomUuidGenerator.class.getName())
          .should(callMethod(UUID.class, "randomUUID"));

  /**
   * The other half of ADR 0005: no field or method anywhere carries {@code @GeneratedValue} - ids
   * are assigned by {@code IdGenerator} before the entity is saved, never by the database or the
   * JPA provider.
   */
  @ArchTest
  static final ArchRule noGeneratedValueIds =
      noMembers().should().beAnnotatedWith(GeneratedValue.class);

  /**
   * "Never overload a handler method name within a controller" (operation ids): {@code
   * OpenApiConfig} pins each operation id to {@code <controller>_<method>}, so two handlers sharing
   * a method name in one controller would collapse to one id and silently drop an operation from
   * the generated {@code schema.ts}. Handler = any method meta-annotated with {@link
   * RequestMapping} ({@code @GetMapping}, {@code @PatchMapping}, ...).
   */
  @ArchTest
  static final ArchRule controllerHandlerMethodNamesAreUnique =
      classes().that().areAnnotatedWith(RestController.class).should(haveUniqueHandlerNames());

  /**
   * "{@code infrastructure/persistence} holds the {@code AuditableEntity} base every table-backed
   * entity extends". {@link AuditableEntity} itself is a {@code @MappedSuperclass}, not a
   * {@code @Entity}, so it's never a target of its own rule and needs no self-exclusion.
   */
  @ArchTest
  static final ArchRule everyEntityExtendsAuditableEntity =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .beAssignableTo(AuditableEntity.class);

  /**
   * "package-private Spring Data {@code JpaRepository}": only the repository adapter in the same
   * package may use it.
   */
  @ArchTest
  static final ArchRule jpaRepositoriesAreNotPublic =
      classes()
          .that()
          .areInterfaces()
          .and()
          .haveSimpleNameEndingWith("JpaRepository")
          .should()
          .notBePublic();

  /**
   * The framework-isolation half of ADR 0004/Hexagonal Architecture and ADR 0005 (Lombok forbidden
   * on domain classes): nothing in {@code domain..} depends on Spring, Jakarta or Lombok - with one
   * documented exception. {@code TransactionRepository} and {@code TransferRepository} (both domain
   * repository ports) depend on {@code org.springframework.data.domain.Page}/{@code Pageable}; see
   * those ports' own javadoc - spring-data-commons' framework-agnostic paging types are used
   * directly rather than inventing a parallel paging abstraction. Only that subpackage is exempted,
   * not all of {@code org.springframework..}: a domain class depending on, say, {@code
   * org.springframework.stereotype.Component} still fails this rule.
   */
  private static final DescribedPredicate<JavaClass> FORBIDDEN_FRAMEWORK_DEPENDENCY =
      JavaClass.Predicates.resideInAnyPackage("org.springframework..", "jakarta..", "lombok..")
          .and(JavaClass.Predicates.resideInAPackage("org.springframework.data.domain..").negate());

  @ArchTest
  static final ArchRule domainDoesNotDependOnFrameworkTypes =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN_PACKAGE + "..")
          .should()
          .dependOnClassesThat(FORBIDDEN_FRAMEWORK_DEPENDENCY);

  /**
   * "Every public method of an application command service that is {@code @Transactional} and not
   * read-only must be in a class that depends on {@code AuditLog}/{@code AuditRecorder}" (F025
   * spec/plan.md, ADR 0022). Checked at the class level (does the class depend on either type
   * anywhere), not per-method, since one shared {@link AuditRecorder} field serves every write
   * method in a service. Empty as of PR2 (F025 plan.md): every {@code @Transactional} write use
   * case in the application layer now depends on {@link AuditRecorder}.
   *
   * <p>{@code @Transactional(readOnly = true)} ({@code DataExportService}, which only reads) is
   * deliberately excluded by {@link #hasATransactionalWriteMethod()} - it performs no write at all,
   * so it has nothing to audit.
   */
  private static final Set<String> AUDIT_LOG_ALLOWLIST = Set.of();

  @ArchTest
  static final ArchRule writeUseCasesDependOnAuditLog =
      classes()
          .that()
          .resideInAPackage("com.chm.myfinances.application..")
          .and()
          .areAnnotatedWith(Service.class)
          .and(hasATransactionalWriteMethod())
          .and()
          .haveNameNotMatching(allowlistRegex())
          .should()
          .dependOnClassesThat(isAuditPort())
          .as(
              "application @Service classes with a @Transactional write method depend on"
                  + " AuditLog/AuditRecorder (F025, ADR 0022), minus the PR1 allowlist");

  private static DescribedPredicate<JavaClass> hasATransactionalWriteMethod() {
    return new DescribedPredicate<>("has a @Transactional write method") {
      @Override
      public boolean test(JavaClass javaClass) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (!method
              .getModifiers()
              .contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
            continue;
          }
          if (!method.isAnnotatedWith(Transactional.class)) {
            continue;
          }
          boolean readOnly =
              method
                  .tryGetAnnotationOfType(Transactional.class)
                  .map(Transactional::readOnly)
                  .orElse(false);
          if (!readOnly) {
            return true;
          }
        }
        return false;
      }
    };
  }

  private static DescribedPredicate<JavaClass> isAuditPort() {
    return JavaClass.Predicates.assignableTo(AuditLog.class)
        .or(JavaClass.Predicates.assignableTo(AuditRecorder.class));
  }

  private static String allowlistRegex() {
    return String.join("|", AUDIT_LOG_ALLOWLIST);
  }

  private static ArchCondition<JavaClass> haveUniqueHandlerNames() {
    return new ArchCondition<>("declare each request-handler method name only once") {
      @Override
      public void check(JavaClass controller, ConditionEvents events) {
        Set<String> seen = new HashSet<>();
        for (JavaMethod method : controller.getMethods()) {
          if (method.isMetaAnnotatedWith(RequestMapping.class) && !seen.add(method.getName())) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "%s has more than one handler named '%s' (duplicate operation id)",
                        controller.getFullName(), method.getName())));
          }
        }
      }
    };
  }

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
          if (originAggregate.equals("transaction") && target.isEquivalentTo(CategoryType.class)) {
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
