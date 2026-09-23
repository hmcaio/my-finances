package com.chm.myfinances.testsupport.web;

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
 * Meta-annotation for a REST-layer {@code @SpringBootTest} against the real Testcontainers Postgres
 * (ADR 0010), {@code webEnvironment = MOCK} so a hand-built {@link
 * org.springframework.test.web.servlet.MockMvc} can be used (Boot 4.x removed
 * {@code @AutoConfigureMockMvc}). Bundles the repeated {@code @SpringBootTest(webEnvironment =
 * MOCK) @Import(TestcontainersConfiguration.class) @Transactional} trio (issue #31, B10) so each
 * rolls back automatically between tests.
 *
 * <p>A class that needs an additional {@code @Import} (e.g. a test-only {@code Clock} bean) can
 * still declare its own {@code @Import} alongside this meta-annotation — Spring's configuration
 * class parser collects {@code @Import} from every meta-annotation level and combines them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
public @interface WebIntegrationTest {}
