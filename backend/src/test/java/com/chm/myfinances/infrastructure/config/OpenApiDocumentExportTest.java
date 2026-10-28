package com.chm.myfinances.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;

/**
 * Writes the OpenAPI document to the file named by the {@code openapi.output} system property, so
 * {@code npm run generate-api-types -- --offline} can regenerate the frontend's {@code schema.ts}
 * without a running backend, a login or a password: the Gradle task {@code exportOpenApi} sets the
 * property and runs only this class. In every ordinary run the property is absent and the test is
 * skipped, so nothing is written.
 */
@WebIntegrationTest
class OpenApiDocumentExportTest {

  @Autowired private WebApplicationContext webApplicationContext;

  @Test
  void writesTheOpenApiDocumentWhenAskedTo() throws Exception {
    String output = System.getProperty("openapi.output");
    assumeTrue(output != null && !output.isBlank(), "openapi.output is not set: nothing to export");

    String document =
        MockMvcSupport.build(webApplicationContext)
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(JsonSupport.MAPPER.readTree(document).get("paths")).isNotEmpty();
    Path target = Path.of(output).toAbsolutePath();
    Files.createDirectories(target.getParent());
    Files.writeString(target, document, StandardCharsets.UTF_8);
  }
}
