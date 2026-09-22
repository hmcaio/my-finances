package com.chm.myfinances.testsupport;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The one-line {@link MockMvc} build every REST-layer test repeats in its own {@code @BeforeEach}
 * (Spring Boot 4.x removed {@code @AutoConfigureMockMvc} - see {@code backend/CLAUDE.md}'s Testing
 * section for why it's built by hand). Extracted for issue #31, B7, mainly so a future change to
 * how {@link MockMvc} is built (e.g. registering a filter) has one place to land.
 */
public final class MockMvcSupport {

  private MockMvcSupport() {}

  public static MockMvc build(WebApplicationContext webApplicationContext) {
    return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }
}
