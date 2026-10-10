package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.investmentreport.DividendHistoryQuery;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DividendIncomeReportGenerator} (F030 spec), written first (ADR 0004): the
 * dividend detail list plus its per-ticker and per-month total sections.
 */
class DividendIncomeReportGeneratorTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final DividendIncomeReportGenerator generator =
      new DividendIncomeReportGenerator(
          new DividendHistoryQuery(
              transactionRepository, categoryRepository, holdingRepository, productRepository));

  @Test
  void rendersDividendsPerTickerTotalsAndPerMonthTotals() {
    Category dividendCategory =
        categoryRepository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Dividends R Test", CategoryType.INCOME, false, false, true));
    InvestmentProduct product =
        productRepository.save(
            InvestmentProductMother.product()
                .withName("KNRI11 Fund R Test")
                .withTicker("KNRI11")
                .build());
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    transactionRepository.save(
        TransactionMother.income()
            .withCategoryId(dividendCategory.getId())
            .withDate(LocalDate.of(2026, 3, 10))
            .withAmount(new BigDecimal("50.00"))
            .build());
    transactionRepository.save(
        TransactionMother.income()
            .withCategoryId(dividendCategory.getId())
            .withDate(LocalDate.of(2026, 4, 10))
            .withAmount(new BigDecimal("60.00"))
            .build());
    // Link the dividend holding on the second one via edit, since TransactionMother has no
    // investmentHoldingId setter - build a fresh transaction with the full 12-arg factory instead.
    transactionRepository.save(
        com.chm.myfinances.domain.transaction.Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 20),
            new BigDecimal("30.00"),
            dividendCategory.getId(),
            CategoryType.INCOME,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "Dividend",
            null,
            null,
            holding.getId()));

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                null,
                null,
                null,
                null,
                null,
                null));

    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 3);
    assertThat(sections.get(0)).hasSize(3);
    assertThat(sections.get(1))
        .anySatisfy(
            r -> {
              assertThat(r.get("ticker")).isEqualTo("KNRI11");
              assertThat(r.get("total_amount")).isEqualTo("30.00");
            });
    assertThat(sections.get(2))
        .extracting(r -> r.get("month"))
        .containsExactlyInAnyOrder("2026-03", "2026-04");
  }
}
