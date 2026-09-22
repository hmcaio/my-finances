package com.chm.myfinances.testsupport.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared JSON plumbing for REST-layer tests (issue #31, B7): one {@link ObjectMapper} instead of a
 * {@code new ObjectMapper()} per controller test class (there's no {@code ObjectMapper} bean - see
 * {@code backend/CLAUDE.md}'s Testing section), and a drop-in for the {@code
 * readTree(...).get("id").asText()} pattern repeated after every {@code POST}.
 */
public final class JsonSupport {

  public static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonSupport() {}

  public static String toJson(Object value) throws JsonProcessingException {
    return MAPPER.writeValueAsString(value);
  }

  /** The {@code "id"} field of a JSON response body, as a plain string. */
  public static String idOf(MvcResult result) throws IOException {
    return MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
