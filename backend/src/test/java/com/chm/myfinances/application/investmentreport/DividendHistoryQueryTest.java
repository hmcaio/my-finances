package com.chm.myfinances.application.investmentreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DividendHistoryQuery} (F026 spec, ADR 0023): filters by product/date range,
 * totals by ticker and by month.
 */
class DividendHistoryQueryTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final DividendHistoryQuery query =
      new DividendHistoryQuery(transactionRepository, categoryRepository, holdingRepository, productRepository);

  private final UUID dividendCategoryId =
      categoryRepository
          .save(
              Category.reconstitute(
                  UUID.randomUUID(), "Dividends", CategoryType.INCOME, false, false, true))
          .getId();

  private InvestmentProduct knri;
  private InvestmentProduct hglg;
  private InvestmentHolding knriHolding;
  private InvestmentHolding hglgHolding;

  private void setUpProducts() {
    knri = productRepository.save(InvestmentProductMother.product().withName("KNRI11 Fund").withTicker("KNRI11").build());
    hglg = productRepository.save(InvestmentProductMother.product().withName("HGLG11 Fund").withTicker("HGLG11").build());
    knriHolding = holdingRepository.save(InvestmentHoldingMother.holding().withProductId(knri.getId()).build());
    hglgHolding = holdingRepository.save(InvestmentHoldingMother.holding().withProductId(hglg.getId()).build());
  }

  private void dividend(LocalDate date, String amount, UUID holdingId) {
    transactionRepository.save(
        Transaction.create(
            UUID.randomUUID(),
            date,
            new BigDecimal(amount),
            dividendCategoryId,
            CategoryType.INCOME,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "Dividend",
            null,
            null,
            holdingId));
  }

  @Test
  void dividendsIsEmptyWhenNoDividendCategoryExistsYet() {
    FakeCategoryRepository emptyCategories = new FakeCategoryRepository();
    DividendHistoryQuery queryWithoutCategory =
        new DividendHistoryQuery(transactionRepository, emptyCategories, holdingRepository, productRepository);

    assertThat(queryWithoutCategory.dividends(null, null, null)).isEmpty();
  }

  @Test
  void dividendsFiltersByProduct() {
    setUpProducts();
    dividend(LocalDate.of(2026, 1, 15), "50.00", knriHolding.getId());
    dividend(LocalDate.of(2026, 1, 20), "30.00", hglgHolding.getId());

    List<DividendRow> rows = query.dividends(knri.getId(), null, null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).ticker()).isEqualTo("KNRI11");
    assertThat(rows.get(0).amount()).isEqualByComparingTo("50.00");
  }

  @Test
  void dividendsFiltersByDateRange() {
    setUpProducts();
    dividend(LocalDate.of(2026, 1, 15), "50.00", knriHolding.getId());
    dividend(LocalDate.of(2026, 3, 15), "60.00", knriHolding.getId());

    List<DividendRow> rows =
        query.dividends(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 1));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void totalsByTickerGroupsCorrectly() {
    setUpProducts();
    dividend(LocalDate.of(2026, 1, 15), "50.00", knriHolding.getId());
    dividend(LocalDate.of(2026, 2, 15), "25.00", knriHolding.getId());
    dividend(LocalDate.of(2026, 1, 20), "30.00", hglgHolding.getId());

    List<DividendTotalByTicker> totals = query.totalsByTicker(null, null);

    assertThat(totals).hasSize(2);
    assertThat(totals.stream().filter(t -> "KNRI11".equals(t.ticker())).findFirst().orElseThrow().amount())
        .isEqualByComparingTo("75.00");
  }

  @Test
  void totalsByMonthGroupsCorrectly() {
    setUpProducts();
    dividend(LocalDate.of(2026, 1, 15), "50.00", knriHolding.getId());
    dividend(LocalDate.of(2026, 1, 20), "30.00", hglgHolding.getId());
    dividend(LocalDate.of(2026, 2, 10), "40.00", knriHolding.getId());

    List<DividendTotalByMonth> totals = query.totalsByMonth(null, null);

    assertThat(totals).hasSize(2);
    assertThat(totals.get(0).month()).isEqualTo(java.time.YearMonth.of(2026, 1));
    assertThat(totals.get(0).amount()).isEqualByComparingTo("80.00");
    assertThat(totals.get(1).amount()).isEqualByComparingTo("40.00");
  }
}
