package com.chm.myfinances;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * Rules about the test tree itself (the sibling of {@link ArchitectureTest}, which scans main
 * sources only).
 */
@AnalyzeClasses(
    packages = "com.chm.myfinances",
    importOptions = ImportOption.OnlyIncludeTests.class)
class TestConventionsTest {

  /**
   * {@code SecurityMockMvcRequestPostProcessors.csrf()} permanently swaps the shared,
   * context-cached filter chain's CSRF repository for a session-based one, after which no {@code
   * XSRF-TOKEN} cookie is written in that JVM and cookie-behavior tests pass or fail depending on
   * class order; {@code user()} bypasses the real session login the same way. {@code
   * MockMvcSupport.build} already supplies an authenticated session and a matching cookie/header
   * pair.
   */
  @ArchTest
  static final ArchRule testsDoNotUseSecurityCsrfOrUserPostProcessors =
      noClasses()
          .should(
              callMethodWhere(
                  target(
                          owner(
                              JavaClass.Predicates.equivalentTo(
                                  SecurityMockMvcRequestPostProcessors.class)))
                      .and(target(nameMatching("csrf|user")))))
          .because(
              "MockMvcSupport.build is already authenticated, and csrf() poisons the shared filter chain (F036)");
}
