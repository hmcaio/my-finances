package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts rows into the four locked taxonomies for real-DB tests (F034/ADR 0028, issue #253). Their
 * repository ports are read-only because every row is migration-seeded content, so a test that
 * needs an extra category, sub-category, segment or payment method adds it with plain SQL here
 * instead of through a write path that production never uses.
 *
 * <p>The insert runs on the same connection as the surrounding {@code @Transactional} test (so it
 * rolls back with it) and is visible to the repository adapters' reads at once, with no flush. The
 * names must still avoid the migration seeds (backend-testing.md): the unique constraints reject a
 * repeat.
 */
public final class TaxonomySeeder {

  private TaxonomySeeder() {}

  /** Inserts and returns a {@link PaymentMethod} with the given name. */
  public static PaymentMethod paymentMethod(JdbcTemplate jdbc, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payment_methods (id, name, created_at, last_modified_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return PaymentMethod.reconstitute(id, name);
  }

  /** Inserts and returns an {@link InvestmentCategory} with the given name. */
  public static InvestmentCategory investmentCategory(JdbcTemplate jdbc, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO investment_categories (id, name, created_at, last_modified_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return InvestmentCategory.reconstitute(id, name);
  }

  /** Inserts and returns an {@link InvestmentSubcategory} under the given category. */
  public static InvestmentSubcategory investmentSubcategory(
      JdbcTemplate jdbc, UUID investmentCategoryId, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO investment_subcategories"
            + " (id, investment_category_id, name, created_at, last_modified_at)"
            + " VALUES (?, ?, ?, now(), now())",
        id,
        investmentCategoryId,
        name);
    return InvestmentSubcategory.reconstitute(id, investmentCategoryId, name);
  }

  /** Inserts and returns an {@link InvestmentSegment} with the given name. */
  public static InvestmentSegment investmentSegment(JdbcTemplate jdbc, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO investment_segments (id, name, created_at, last_modified_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return InvestmentSegment.reconstitute(id, name);
  }
}
