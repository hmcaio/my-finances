package com.chm.myfinances.infrastructure.web.vehicle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link VehicleController}, against a real Testcontainers Postgres
 * (ADR 0010). Mirrors {@code PaymentMethodControllerTest}.
 */
@WebIntegrationTest
class VehicleControllerTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void createListRenameAndDeleteRoundTrip() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Test Vehicle"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    mockMvc
        .perform(get("/api/vehicles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Test Vehicle')]").exists());

    String renameBody = objectMapper.writeValueAsString(Map.of("name", "Renamed Vehicle"));
    mockMvc
        .perform(
            patch("/api/vehicles/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Vehicle"));

    mockMvc.perform(delete("/api/vehicles/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/vehicles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Renamed Vehicle')]").doesNotExist());
  }

  @Test
  void deleteRejectsAVehicleReferencedByAFuelTransactionWith409() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "In Use Vehicle"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    Account account =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Checking");
    Category fuelCategory =
        categoryRepository.findAll().stream()
            .filter(Category::isFuelCategory)
            .findFirst()
            .orElseThrow();
    var paymentMethod = TestFixtures.paymentMethod(paymentMethodRepository, "Debit Card Test");
    transactionRepository.save(
        TransactionMother.expense()
            .withAmount(BigDecimal.TEN)
            .withCategoryId(fuelCategory.getId())
            .withAccountId(account.getId())
            .withPaymentMethodId(paymentMethod.getId())
            .withDescription("In-use fuel transaction")
            .withFuelDetails(
                new FuelDetails(
                    UUID.fromString(id),
                    FuelType.GASOLINA,
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    null,
                    null))
            .build());

    mockMvc.perform(delete("/api/vehicles/" + id)).andExpect(status().isConflict());
  }

  @Test
  void createRejectsADuplicateNameWith409() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Duplicate Vehicle"));
    mockMvc
        .perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsNameOverMaxLength() throws Exception {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);
    String createBody = objectMapper.writeValueAsString(Map.of("name", tooLongName));

    mockMvc
        .perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void fuelHistoryOfUnknownVehicleReturns404() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + UUID.randomUUID() + "/fuel-history"))
        .andExpect(status().isNotFound());
  }

  @Test
  void fuelHistoryReturnsOnlyThatVehiclesFuelTransactionsOrderedByDateWithRatios()
      throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "History Vehicle"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    UUID vehicleId = UUID.fromString(JsonSupport.idOf(createResult));

    Account account =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Checking History");
    Category fuelCategory =
        categoryRepository.findAll().stream()
            .filter(Category::isFuelCategory)
            .findFirst()
            .orElseThrow();
    var paymentMethod =
        TestFixtures.paymentMethod(paymentMethodRepository, "Debit Card History Test");

    transactionRepository.save(
        TransactionMother.expense()
            .withDate(LocalDate.of(2026, 2, 1))
            .withAmount(new BigDecimal("200.00"))
            .withCategoryId(fuelCategory.getId())
            .withAccountId(account.getId())
            .withPaymentMethodId(paymentMethod.getId())
            .withDescription("Second fill")
            .withFuelDetails(
                new FuelDetails(
                    vehicleId,
                    FuelType.GASOLINA,
                    new BigDecimal("40"),
                    new BigDecimal("5"),
                    new BigDecimal("400.0"),
                    null))
            .build());
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(LocalDate.of(2026, 1, 1))
            .withAmount(new BigDecimal("150.00"))
            .withCategoryId(fuelCategory.getId())
            .withAccountId(account.getId())
            .withPaymentMethodId(paymentMethod.getId())
            .withDescription("First fill")
            .withFuelDetails(
                new FuelDetails(
                    vehicleId,
                    FuelType.GASOLINA,
                    new BigDecimal("30"),
                    new BigDecimal("5"),
                    null,
                    null))
            .build());

    mockMvc
        .perform(get("/api/vehicles/" + vehicleId + "/fuel-history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].date").value("2026-01-01"))
        .andExpect(jsonPath("$[0].kmPerLiter").doesNotExist())
        .andExpect(jsonPath("$[1].date").value("2026-02-01"))
        .andExpect(jsonPath("$[1].kmPerLiter").value(10.0));
  }
}
