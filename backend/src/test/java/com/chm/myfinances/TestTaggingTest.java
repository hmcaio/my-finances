package com.chm.myfinances;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Keeps the two Gradle tiers honest: `test` excludes the JUnit tag `integration`, so a
 * {@code @SpringBootTest} class without it would silently run in the fast tier (and need Docker).
 * The tag is normally inherited through {@code @DatabaseIntegrationTest} /
 * {@code @WebIntegrationTest}.
 */
@AnalyzeClasses(
    packages = "com.chm.myfinances",
    importOptions = ImportOption.OnlyIncludeTests.class)
class TestTaggingTest {

  @ArchTest
  static final ArchRule springBootTestsAreTaggedIntegration =
      classes()
          .that()
          .areMetaAnnotatedWith(SpringBootTest.class)
          .should()
          .beMetaAnnotatedWith(Tag.class)
          .because("build.gradle runs the `integration` tag in the integrationTest task only");
}
