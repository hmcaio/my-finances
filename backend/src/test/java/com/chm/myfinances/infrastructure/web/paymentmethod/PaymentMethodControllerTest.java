package com.chm.myfinances.infrastructure.web.paymentmethod;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link PaymentMethodController}, against a real Testcontainers
 * Postgres (ADR 0010). See {@code CategoryControllerTest} for why {@link MockMvc} is built by hand
 * here instead of via {@code @AutoConfigureMockMvc} (removed in Boot 4.x).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were removed — every row is now
 * fixed, migration-seeded content (the four seeded rows), reachable only by {@code GET}.
 */
@WebIntegrationTest
class PaymentMethodControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private PaymentMethodRepository paymentMethodRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void listIncludesTheSeededPaymentMethods() throws Exception {
    mockMvc
        .perform(get("/api/payment-methods"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Debit Card')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Credit Card')]").exists())
        .andExpect(jsonPath("$[?(@.name=='PIX')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Cash')]").exists());
  }

  @Test
  void postIsNoLongerSupported() throws Exception {
    mockMvc
        .perform(
            post("/api/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nope Test"))))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void patchIsNoLongerSupported() throws Exception {
    PaymentMethod paymentMethod =
        paymentMethodRepository.save(
            PaymentMethod.reconstitute(UUID.randomUUID(), "Immutable Test"));

    mockMvc
        .perform(
            patch("/api/payment-methods/" + paymentMethod.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Test"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteIsNoLongerSupported() throws Exception {
    PaymentMethod paymentMethod =
        paymentMethodRepository.save(
            PaymentMethod.reconstitute(UUID.randomUUID(), "Undeletable Test"));

    mockMvc
        .perform(delete("/api/payment-methods/" + paymentMethod.getId()))
        .andExpect(status().isNotFound());
  }
}
