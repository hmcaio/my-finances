package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.AbstractTransactionalBoundaryTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) of F040's two
 * transaction claims for {@link InvestmentSnapshotService#recordBatch}:
 *
 * <ul>
 *   <li>{@code recordBatch} calls {@code record}, itself {@code @Transactional}, on the same bean:
 *       the inner call runs in the outer transaction, so a failure after the first row takes back
 *       that row's snapshot AND its audit entry.
 *   <li>A unique-constraint violation raised by a later row inside the batch transaction reaches
 *       the client as the 409 {@code investment-snapshot-date-taken} and leaves nothing behind.
 * </ul>
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} - see {@code
 * docs/conventions/backend-testing.md}. The committed holdings, products and snapshots are removed
 * in {@link #deleteFixtures()}; the account stays (no delete port), so it has a unique name.
 */
class InvestmentSnapshotSheetTransactionalTest extends AbstractTransactionalBoundaryTest {

  private static final LocalDate DATE = LocalDate.of(2026, 5, 31);

  @Autowired private InvestmentSnapshotService snapshotService;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private WebApplicationContext webApplicationContext;

  private final List<UUID> holdingIds = new ArrayList<>();
  private final List<UUID> productIds = new ArrayList<>();
  private UUID brokerId;

  @BeforeEach
  void setUp() {
    brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Sheet Rollback " + UUID.randomUUID().toString().substring(0, 8),
                AccountType.INVESTMENT)
            .getId();
  }

  @AfterEach
  void deleteFixtures() {
    holdingIds.forEach(
        id -> {
          jdbc.update(
              "DELETE FROM audit_log WHERE entity_type = 'INVESTMENT_SNAPSHOT' AND entity_id IN"
                  + " (SELECT id FROM investment_snapshots WHERE holding_id = ?)",
              id);
          jdbc.update("DELETE FROM investment_snapshots WHERE holding_id = ?", id);
          jdbc.update("DELETE FROM investment_holdings WHERE id = ?", id);
        });
    productIds.forEach(id -> jdbc.update("DELETE FROM investment_products WHERE id = ?", id));
  }

  private UUID newHolding() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    categoryRepository.findAll().get(0).getId(),
                    null,
                    "Product Sheet Rollback " + tag,
                    null))
            .getId();
    UUID holdingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
            .getId();
    productIds.add(productId);
    holdingIds.add(holdingId);
    return holdingId;
  }

  private int snapshotRows(UUID holdingId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM investment_snapshots WHERE holding_id = ?", Integer.class, holdingId);
  }

  private int snapshotAuditRows() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'INVESTMENT_SNAPSHOT'", Integer.class);
  }

  @Test
  void aFailureAfterTheFirstRowLeavesNoSnapshotAndNoAuditEntryBehind() {
    UUID first = newHolding();
    UUID second = newHolding();
    int auditBefore = snapshotAuditRows();
    willCallRealMethod()
        .willThrow(new RuntimeException("simulated failure writing the second snapshot"))
        .given(snapshotRepository)
        .save(any(InvestmentSnapshot.class));

    assertThatThrownBy(
            () ->
                snapshotService.recordBatch(
                    DATE,
                    List.of(
                        new SnapshotBatchEntry(first, new BigDecimal("10.00")),
                        new SnapshotBatchEntry(second, new BigDecimal("20.00")))))
        .isInstanceOf(RuntimeException.class);

    assertThat(snapshotRows(first)).isZero();
    assertThat(snapshotRows(second)).isZero();
    assertThat(snapshotAuditRows()).isEqualTo(auditBefore);
  }

  @Test
  void aUniqueViolationOnALaterRowSurfacesAsTheDateTaken409AndRollsTheBatchBack() throws Exception {
    UUID first = newHolding();
    UUID second = newHolding();
    int auditBefore = snapshotAuditRows();
    willCallRealMethod()
        .willThrow(new DataIntegrityViolationException("uq_investment_snapshots_holding_date"))
        .given(snapshotRepository)
        .save(any(InvestmentSnapshot.class));
    MockMvc mockMvc = MockMvcSupport.build(webApplicationContext);

    mockMvc
        .perform(
            post("/api/investment-snapshot-sheet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-05-31\",\"entries\":[{\"holdingId\":\""
                        + first
                        + "\",\"balance\":10.00},{\"holdingId\":\""
                        + second
                        + "\",\"balance\":20.00}]}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value("urn:my-finances:error:investment-snapshot-date-taken"));

    assertThat(snapshotRows(first)).isZero();
    assertThat(snapshotRows(second)).isZero();
    assertThat(snapshotAuditRows()).isEqualTo(auditBefore);
  }
}
