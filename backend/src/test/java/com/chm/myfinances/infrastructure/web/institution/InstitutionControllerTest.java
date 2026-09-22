package com.chm.myfinances.infrastructure.web.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.TestInstitutions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InstitutionController}, against a real Testcontainers
 * Postgres (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F002's {@code
 * CategoryControllerTest}. The migration-seeded built-in row is always present, so nothing here
 * assumes the list is empty or of a fixed size, and fixture names carry the {@code " Test"} suffix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class InstitutionControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private AccountRepository accountRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  private String createInstitution(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/institutions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void createAccountAt(String institutionId, boolean closed) {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Account At Institution Test " + UUID.randomUUID(),
            UUID.fromString(institutionId),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    if (closed) {
      account.close(LocalDate.now());
    }
    accountRepository.save(account);
  }

  private String builtInId() {
    return TestInstitutions.builtInId(institutionRepository).toString();
  }

  @Test
  void createReturnsAnInstitutionThatIsNotBuiltIn() throws Exception {
    mockMvc
        .perform(
            post("/api/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nubank Test"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Nubank Test"))
        .andExpect(jsonPath("$.builtIn").value(false));
  }

  @Test
  void createIgnoresABuiltInFieldInTheBody() throws Exception {
    // The flag is not part of the request DTO: a client cannot create a built-in row.
    mockMvc
        .perform(
            post("/api/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Sneaky Built-in Test", "builtIn", true))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.builtIn").value(false));
  }

  @Test
  void listIncludesTheBuiltInRowAndCreatedInstitutionsSortedByName() throws Exception {
    createInstitution("Zzz Bank Test");
    createInstitution("Aaa Bank Test");

    MvcResult result =
        mockMvc
            .perform(get("/api/institutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.builtIn==true)].name").value("No institution"))
            .andExpect(jsonPath("$[?(@.name=='Aaa Bank Test')]").exists())
            .andExpect(jsonPath("$[?(@.name=='Zzz Bank Test')]").exists())
            .andReturn();

    List<String> names =
        objectMapper.readTree(result.getResponse().getContentAsString()).findValuesAsText("name");
    assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
  }

  @Test
  void renameReplacesTheName() throws Exception {
    String id = createInstitution("Original Test");

    mockMvc
        .perform(
            patch("/api/institutions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.name").value("Renamed Test"))
        .andExpect(jsonPath("$.builtIn").value(false));
  }

  @Test
  void theBuiltInInstitutionCanBeRenamedAndStaysBuiltIn() throws Exception {
    mockMvc
        .perform(
            patch("/api/institutions/" + builtInId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Sem instituicao Test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Sem instituicao Test"))
        .andExpect(jsonPath("$.builtIn").value(true));
  }

  @Test
  void deleteReturns204AndTheInstitutionIsGone() throws Exception {
    String id = createInstitution("Temp Test");

    mockMvc.perform(delete("/api/institutions/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/institutions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + id + "')]").doesNotExist());
  }

  @Test
  void createRejectsADuplicateNameWith409() throws Exception {
    createInstitution("Duplicate Test");

    mockMvc
        .perform(
            post("/api/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Duplicate Test"))))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsTheBuiltInRowsNameWith409() throws Exception {
    mockMvc
        .perform(
            post("/api/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "No institution"))))
        .andExpect(status().isConflict());
  }

  @Test
  void renameRejectsADuplicateNameWith409() throws Exception {
    createInstitution("Taken Test");
    String id = createInstitution("To Rename Test");

    mockMvc
        .perform(
            patch("/api/institutions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Taken Test"))))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteRejectsAnInstitutionUsedByAnOpenAccountWith409() throws Exception {
    String id = createInstitution("Used Test");
    createAccountAt(id, false);

    mockMvc.perform(delete("/api/institutions/" + id)).andExpect(status().isConflict());
  }

  @Test
  void deleteRejectsAnInstitutionUsedByAClosedAccountWith409() throws Exception {
    String id = createInstitution("Used By Closed Test");
    createAccountAt(id, true);

    mockMvc.perform(delete("/api/institutions/" + id)).andExpect(status().isConflict());
  }

  @Test
  void deleteRejectsTheBuiltInInstitutionWith409() throws Exception {
    mockMvc.perform(delete("/api/institutions/" + builtInId())).andExpect(status().isConflict());
  }

  @Test
  void renamedBuiltInInstitutionStillCannotBeDeleted() throws Exception {
    mockMvc
        .perform(
            patch("/api/institutions/" + builtInId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Built-in Test"))))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/api/institutions/" + builtInId())).andExpect(status().isConflict());
  }

  @Test
  void renameOfAnUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            patch("/api/institutions/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Whatever Test"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteOfAnUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(delete("/api/institutions/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRejectsABlankNameWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "  "))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsANameOverMaxLengthWith400() throws Exception {
    String tooLong = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    mockMvc
        .perform(
            post("/api/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", tooLong))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void renameRejectsABlankOrTooLongNameWith400() throws Exception {
    String id = createInstitution("Validation Test");

    mockMvc
        .perform(
            patch("/api/institutions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", " "))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            patch("/api/institutions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1)))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aMalformedIdReturns400() throws Exception {
    mockMvc.perform(delete("/api/institutions/not-a-uuid")).andExpect(status().isBadRequest());
  }
}
