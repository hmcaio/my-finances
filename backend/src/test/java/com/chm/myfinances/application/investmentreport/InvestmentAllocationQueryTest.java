package com.chm.myfinances.application.investmentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InvestmentAllocationQuery} (F009 spec), written first (ADR 0004): latest
 * snapshot per product as of a date, grouped by category or by category then sub-category. Products
 * with a {@code 0} or missing snapshot add nothing to a total; a group's {@code needsSnapshot} is
 * true when any of its products is stale.
 */
class InvestmentAllocationQueryTest {

  private static final LocalDate ASOF = LocalDate.of(2026, 6, 30);

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final LatestInvestmentSnapshotQuery latestQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository);
  private final InvestmentAllocationQuery query =
      new InvestmentAllocationQuery(
          productRepository,
          categoryRepository,
          subcategoryRepository,
          latestQuery,
          new InvestmentSnapshotFreshnessQuery(latestQuery, transferRepository));

  private final UUID fixedIncome = category("Fixed Income");
  private final UUID variableIncome = category("Variable Income");
  private final UUID crypto = category("Crypto");
  private final UUID cdb = subcategory(fixedIncome, "CDB");
  private final UUID selicSub = subcategory(fixedIncome, "Tesouro Selic");
  private final UUID stocks = subcategory(variableIncome, "Stocks");

  private UUID category(String name) {
    return categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), name)).getId();
  }

  private UUID subcategory(UUID categoryId, String name) {
    return subcategoryRepository
        .save(InvestmentSubcategory.create(UUID.randomUUID(), categoryId, name))
        .getId();
  }

  private InvestmentProduct product(String name, UUID categoryId, UUID subcategoryId) {
    return productRepository.save(
        InvestmentProductMother.product()
            .withName(name)
            .withInvestmentCategoryId(categoryId)
            .withInvestmentSubcategoryId(subcategoryId)
            .build());
  }

  private void snapshot(InvestmentProduct product, LocalDate date, String balance) {
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), product.getId(), date, new BigDecimal(balance)));
  }

  private void trade(InvestmentProduct product, LocalDate date) {
    transferRepository.save(
        TransferMother.transfer().withDate(date).withInvestmentProductId(product.getId()).build());
  }

  @Test
  void byCategorySumsTheLatestSnapshotOfEveryProductInTheCategory() {
    InvestmentProduct a = product("CDB A", fixedIncome, cdb);
    InvestmentProduct b = product("Selic", fixedIncome, selicSub);
    InvestmentProduct c = product("PETR4", variableIncome, stocks);
    snapshot(a, LocalDate.of(2026, 5, 31), "100.00");
    snapshot(a, LocalDate.of(2026, 6, 15), "150.00");
    snapshot(b, LocalDate.of(2026, 6, 1), "50.00");
    snapshot(c, LocalDate.of(2026, 6, 10), "300.00");

    List<AllocationRow> rows = query.allocation(ASOF, AllocationGrouping.CATEGORY);

    assertThat(rows)
        .extracting(AllocationRow::categoryName, r -> r.totalValue().toPlainString())
        .containsExactly(tuple("Fixed Income", "200.00"), tuple("Variable Income", "300.00"));
    assertThat(rows).allSatisfy(r -> assertThat(r.subcategoryId()).isNull());
    assertThat(rows)
        .extracting(AllocationRow::categoryId)
        .containsExactly(fixedIncome, variableIncome);
  }

  @Test
  void bySubcategoryGroupsByCategoryThenSubcategoryWithANullSliceForProductsWithout() {
    InvestmentProduct a = product("CDB A", fixedIncome, cdb);
    InvestmentProduct b = product("Selic", fixedIncome, selicSub);
    InvestmentProduct bare = product("Fixed misc", fixedIncome, null);
    InvestmentProduct btc = product("Bitcoin", crypto, null);
    snapshot(a, LocalDate.of(2026, 6, 1), "100.00");
    snapshot(b, LocalDate.of(2026, 6, 1), "50.00");
    snapshot(bare, LocalDate.of(2026, 6, 1), "25.00");
    snapshot(btc, LocalDate.of(2026, 6, 1), "10.00");

    List<AllocationRow> rows = query.allocation(ASOF, AllocationGrouping.SUBCATEGORY);

    assertThat(rows)
        .extracting(
            AllocationRow::categoryName,
            AllocationRow::subcategoryName,
            r -> r.totalValue().toPlainString())
        .containsExactly(
            tuple("Crypto", null, "10.00"),
            tuple("Fixed Income", "CDB", "100.00"),
            tuple("Fixed Income", "Tesouro Selic", "50.00"),
            tuple("Fixed Income", null, "25.00"));
    AllocationRow nullSlice =
        rows.stream()
            .filter(r -> r.categoryId().equals(fixedIncome) && r.subcategoryId() == null)
            .findFirst()
            .orElseThrow();
    assertThat(nullSlice.subcategoryName()).isNull();
  }

  @Test
  void aCategoryTotalEqualsTheSumOfItsSubcategoryRows() {
    snapshot(product("CDB A", fixedIncome, cdb), LocalDate.of(2026, 6, 1), "100.10");
    snapshot(product("Selic", fixedIncome, selicSub), LocalDate.of(2026, 6, 1), "50.20");
    snapshot(product("Fixed misc", fixedIncome, null), LocalDate.of(2026, 6, 1), "25.30");

    BigDecimal categoryTotal =
        query.allocation(ASOF, AllocationGrouping.CATEGORY).get(0).totalValue();
    BigDecimal subTotal =
        query.allocation(ASOF, AllocationGrouping.SUBCATEGORY).stream()
            .map(AllocationRow::totalValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(categoryTotal).isEqualByComparingTo("175.60");
    assertThat(subTotal).isEqualByComparingTo(categoryTotal);
  }

  @Test
  void zeroAndMissingSnapshotsAddNothingAndProduceNoRow() {
    InvestmentProduct sold = product("Sold", fixedIncome, cdb);
    product("No snapshot yet", variableIncome, stocks);
    snapshot(sold, LocalDate.of(2026, 5, 1), "500.00");
    snapshot(sold, LocalDate.of(2026, 6, 1), "0.00");

    assertThat(query.allocation(ASOF, AllocationGrouping.CATEGORY)).isEmpty();
    assertThat(query.allocation(ASOF, AllocationGrouping.SUBCATEGORY)).isEmpty();
  }

  @Test
  void aZeroedProductInAPopulatedCategoryDoesNotChangeItsTotal() {
    InvestmentProduct sold = product("Sold", fixedIncome, cdb);
    InvestmentProduct held = product("Held", fixedIncome, cdb);
    snapshot(sold, LocalDate.of(2026, 6, 1), "0.00");
    snapshot(held, LocalDate.of(2026, 6, 1), "80.00");

    List<AllocationRow> rows = query.allocation(ASOF, AllocationGrouping.CATEGORY);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).totalValue()).isEqualByComparingTo("80.00");
  }

  @Test
  void asOfIgnoresLaterSnapshots() {
    InvestmentProduct a = product("CDB A", fixedIncome, cdb);
    snapshot(a, LocalDate.of(2026, 5, 31), "100.00");
    snapshot(a, LocalDate.of(2026, 7, 31), "999.00");

    List<AllocationRow> rows = query.allocation(ASOF, AllocationGrouping.CATEGORY);

    assertThat(rows.get(0).totalValue()).isEqualByComparingTo("100.00");
    assertThat(query.allocation(LocalDate.of(2026, 5, 30), AllocationGrouping.CATEGORY)).isEmpty();
  }

  @Test
  void needsSnapshotIsTrueWhenAnyProductInTheGroupIsStale() {
    InvestmentProduct stale = product("Stale", fixedIncome, cdb);
    InvestmentProduct fresh = product("Fresh", fixedIncome, selicSub);
    InvestmentProduct otherFresh = product("PETR4", variableIncome, stocks);
    snapshot(stale, LocalDate.of(2026, 5, 1), "100.00");
    trade(stale, LocalDate.of(2026, 6, 10));
    snapshot(fresh, LocalDate.of(2026, 6, 20), "50.00");
    trade(fresh, LocalDate.of(2026, 6, 10));
    snapshot(otherFresh, LocalDate.of(2026, 6, 1), "10.00");

    List<AllocationRow> byCategory = query.allocation(ASOF, AllocationGrouping.CATEGORY);
    List<AllocationRow> bySub = query.allocation(ASOF, AllocationGrouping.SUBCATEGORY);

    assertThat(byCategory)
        .extracting(AllocationRow::categoryName, AllocationRow::needsSnapshot)
        .containsExactly(tuple("Fixed Income", true), tuple("Variable Income", false));
    assertThat(bySub)
        .extracting(AllocationRow::subcategoryName, AllocationRow::needsSnapshot)
        .containsExactly(tuple("CDB", true), tuple("Tesouro Selic", false), tuple("Stocks", false));
  }

  @Test
  void aStaleProductWithNoValueYetStillFlagsItsGroupSoTheWarningIsNotLost() {
    InvestmentProduct justBought = product("Just bought", crypto, null);
    trade(justBought, LocalDate.of(2026, 6, 10));

    List<AllocationRow> rows = query.allocation(ASOF, AllocationGrouping.CATEGORY);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).totalValue()).isEqualByComparingTo("0");
    assertThat(rows.get(0).needsSnapshot()).isTrue();
  }
}
