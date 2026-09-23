package com.chm.myfinances.testsupport;

import com.chm.myfinances.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meta-annotation for a non-web {@code @SpringBootTest} against the real Testcontainers Postgres
 * (ADR 0010): persistence adapter tests, and application-layer tests that exercise the real DB
 * rather than a {@code Fake*Repository}. Bundles the repeated {@code @SpringBootTest @Import(
 * TestcontainersConfiguration.class) @Transactional} trio (issue #31, B10) so each rolls back
 * automatically between tests.
 *
 * <p>Do not use on a {@code *TransactionalTest} class (must carry no {@code @Transactional}, or the
 * service under test just joins the surrounding test transaction and rollback can't be observed) or
 * a concurrency test (must run its writes in real, separate transactions).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
public @interface DatabaseIntegrationTest {}
