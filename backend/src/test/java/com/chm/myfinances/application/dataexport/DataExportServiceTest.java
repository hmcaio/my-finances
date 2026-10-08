package com.chm.myfinances.application.dataexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.allocationplan.AllocationPlan;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanRepository;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeBudgetRepository;
import com.chm.myfinances.testsupport.fakes.FakeBudgetVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInstitutionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakePaymentMethodRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.fakes.FakeVehicleRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.BudgetVersionMother;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateMother;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateVersionMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Filter applicability per PRD S6.9: each filter touches only its documented files, reference files
 * stay full, no filter exports everything. Names in this fixture never contain commas or quotes, so
 * rows are split naively; escaping is {@link CsvWriterTest}'s job.
 */
class DataExportServiceTest {

  private static final List<String> FILES =
      List.of(
          "categories.csv",
          "payment_methods.csv",
          "institutions.csv",
          "accounts.csv",
          "transactions.csv",
          "transfers.csv",
          "budgets.csv",
          "recurring_templates.csv",
          "allocation_plan_entries.csv",
          "investment_categories.csv",
          "investment_segments.csv",
          "investment_subcategories.csv",
          "investment_products.csv",
          "investment_holdings.csv",
          "investment_snapshots.csv");

  private final FakeCategoryRepository categories = new FakeCategoryRepository();
  private final FakePaymentMethodRepository paymentMethods = new FakePaymentMethodRepository();
  private final FakeInstitutionRepository institutions = new FakeInstitutionRepository();
  private final FakeAccountRepository accounts = new FakeAccountRepository();
  private final FakeTransactionRepository transactions = new FakeTransactionRepository();
  private final FakeTransferRepository transfers = new FakeTransferRepository();
  private final FakeBudgetRepository budgets = new FakeBudgetRepository();
  private final FakeBudgetVersionRepository budgetVersions = new FakeBudgetVersionRepository();
  private final FakeRecurringTemplateRepository templates = new FakeRecurringTemplateRepository();
  private final FakeRecurringTemplateVersionRepository templateVersions =
      new FakeRecurringTemplateVersionRepository();
  private final FakeInvestmentCategoryRepository investmentCategories =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository investmentSubcategories =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentProductRepository investmentProducts =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository investmentHoldings =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository investmentSnapshots =
      new FakeInvestmentSnapshotRepository();
  private final FakeVehicleRepository vehicles = new FakeVehicleRepository();
  private final FakeInvestmentSegmentRepository investmentSegments =
      new FakeInvestmentSegmentRepository();
  private final FakeAllocationPlanRepository allocationPlans = new FakeAllocationPlanRepository();
  private final FakeAllocationPlanVersionRepository allocationPlanVersions =
      new FakeAllocationPlanVersionRepository();

  private final DataExportService service =
      new DataExportService(
          categories,
          paymentMethods,
          institutions,
          accounts,
          transactions,
          transfers,
          budgets,
          budgetVersions,
          templates,
          templateVersions,
          investmentCategories,
          investmentSubcategories,
          investmentProducts,
          investmentHoldings,
          investmentSnapshots,
          vehicles,
          investmentSegments,
          allocationPlans,
          allocationPlanVersions);

  private final UUID inst = UUID.randomUUID();
  private final UUID food = UUID.randomUUID();
  private final UUID rent = UUID.randomUUID();
  private final UUID salary = UUID.randomUUID();
  private final UUID pm = UUID.randomUUID();
  private final UUID checking = UUID.randomUUID();
  private final UUID savings = UUID.randomUUID();
  private final UUID broker = UUID.randomUUID();
  private final UUID invCat = UUID.randomUUID();
  private final UUID invSub = UUID.randomUUID();
  private final UUID fund = UUID.randomUUID();
  private final UUID bond = UUID.randomUUID();
  private final UUID segment = UUID.randomUUID();
  private final UUID planId = UUID.randomUUID();

  @BeforeEach
  void fixture() {
    institutions.save(Institution.reconstitute(inst, "No institution", true));
    categories.save(Category.create(food, "Food", CategoryType.EXPENSE));
    categories.save(Category.create(rent, "Rent", CategoryType.EXPENSE));
    categories.save(Category.create(salary, "Salary", CategoryType.INCOME));
    paymentMethods.save(PaymentMethod.create(pm, "Debit"));
    accounts.save(
        AccountMother.checking().withId(checking).withName("Main").withInstitutionId(inst).build());
    accounts.save(
        AccountMother.savings()
            .withId(savings)
            .withName("Reserve")
            .withInstitutionId(inst)
            .build());
    accounts.save(
        AccountMother.investment()
            .withId(broker)
            .withName("Broker")
            .withInstitutionId(inst)
            .build());

    transaction("t1", food, checking, "2026-01-10");
    transaction("t2", salary, savings, "2026-02-10");
    transaction("t3", food, savings, "2026-03-10");

    transfer("tr1", checking, savings, "2026-01-15");
    transfer("tr2", savings, checking, "2026-02-15");
    transfers.save(
        TransferMother.transfer()
            .withDescription("tr3")
            .withFromAccountId(checking)
            .withToAccountId(broker)
            .withDate(LocalDate.parse("2026-03-01"))
            .withAmount(new BigDecimal("1000.00"))
            .withInvestmentProductId(fund)
            .withTradeDetails(
                new InvestmentTradeDetails(
                    new BigDecimal("10.5"), new BigDecimal("95.00"), new BigDecimal("2.50")))
            .build());

    Budget foodBudget = budgets.save(Budget.create(UUID.randomUUID(), food));
    Budget rentBudget = budgets.save(Budget.create(UUID.randomUUID(), rent));
    budgetVersion(foodBudget, "2026-01");
    budgetVersion(foodBudget, "2026-03");
    budgetVersion(rentBudget, "2026-02");

    RecurringTemplate foodTemplate =
        templates.save(
            RecurringTemplateMother.template()
                .withCategoryId(food)
                .withAccountId(checking)
                .withDescription("tpl-food")
                .build());
    RecurringTemplate rentTemplate =
        templates.save(
            RecurringTemplateMother.template()
                .withCategoryId(rent)
                .withAccountId(savings)
                .withDescription("tpl-rent")
                .build());
    templateVersion(foodTemplate, "2026-01");
    templateVersion(foodTemplate, "2026-03");
    templateVersion(rentTemplate, "2026-02");

    investmentCategories.save(InvestmentCategory.create(invCat, "Fixed Income"));
    investmentSubcategories.save(InvestmentSubcategory.create(invSub, invCat, "Treasury"));
    investmentSegments.save(InvestmentSegment.create(segment, "Logistica"));
    investmentProducts.save(
        InvestmentProduct.create(fund, invCat, invSub, "Selic 2029", null, "KNRI11", segment));
    investmentProducts.save(InvestmentProduct.create(bond, invCat, null, "Pension", null));
    fundHolding = UUID.randomUUID();
    bondHolding = UUID.randomUUID();
    investmentHoldings.save(InvestmentHolding.create(fundHolding, fund, broker, null));
    investmentHoldings.save(InvestmentHolding.create(bondHolding, bond, broker, null));
    snapshot(fundHolding, "2026-01-31", "100.00");
    snapshot(fundHolding, "2026-02-28", "110.00");
    snapshot(bondHolding, "2026-03-31", "50.00");

    // F026 (ADR 0023): allocation_plan_entries.csv is date-range filtered like budgets.csv.
    allocationPlans.save(AllocationPlan.create(planId));
    allocationPlanVersion("2026-01", fund, "60.00", bond, "40.00");
    allocationPlanVersion("2026-03", fund, "100.00");
  }

  private void allocationPlanVersion(String month, UUID product1, String pct1) {
    allocationPlanVersions.save(
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            planId,
            List.of(new AllocationPlanEntry(product1, new BigDecimal(pct1))),
            YearMonth.parse(month)));
  }

  private void allocationPlanVersion(
      String month, UUID product1, String pct1, UUID product2, String pct2) {
    allocationPlanVersions.save(
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            planId,
            List.of(
                new AllocationPlanEntry(product1, new BigDecimal(pct1)),
                new AllocationPlanEntry(product2, new BigDecimal(pct2))),
            YearMonth.parse(month)));
  }

  private UUID fundHolding;
  private UUID bondHolding;

  private void transaction(String description, UUID category, UUID account, String date) {
    transactions.save(
        TransactionMother.expense()
            .withDescription(description)
            .withCategoryId(category)
            .withAccountId(account)
            .withPaymentMethodId(pm)
            .withDate(LocalDate.parse(date))
            .build());
  }

  private void transfer(String description, UUID from, UUID to, String date) {
    transfers.save(
        TransferMother.transfer()
            .withDescription(description)
            .withFromAccountId(from)
            .withToAccountId(to)
            .withDate(LocalDate.parse(date))
            .build());
  }

  private void budgetVersion(Budget budget, String month) {
    budgetVersions.save(
        BudgetVersionMother.version()
            .withBudgetId(budget.getId())
            .withEffectiveFrom(YearMonth.parse(month))
            .build());
  }

  private void templateVersion(RecurringTemplate template, String month) {
    templateVersions.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withEffectiveFrom(YearMonth.parse(month))
            .build());
  }

  private void snapshot(UUID holdingId, String date, String balance) {
    investmentSnapshots.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holdingId, LocalDate.parse(date), new BigDecimal(balance)));
  }

  private Map<String, List<Map<String, String>>> export(ExportFilter filter) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.export(filter, out);
    Map<String, List<Map<String, String>>> files = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        String[] lines = new String(zip.readAllBytes(), StandardCharsets.UTF_8).split("\r\n", -1);
        String[] header = lines[0].split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
          if (lines[i].isEmpty()) {
            continue;
          }
          String[] cells = lines[i].split(",", -1);
          Map<String, String> row = new LinkedHashMap<>();
          for (int c = 0; c < header.length; c++) {
            row.put(header[c], cells[c]);
          }
          rows.add(row);
        }
        files.put(entry.getName(), rows);
      }
    }
    return files;
  }

  private static List<String> column(
      Map<String, List<Map<String, String>>> files, String file, String column) {
    return files.get(file).stream().map(r -> r.get(column)).toList();
  }

  @Test
  void noFilterExportsAllFifteenFilesInFull() throws IOException {
    var files = export(ExportFilter.none());

    assertThat(files.keySet()).containsExactlyElementsOf(FILES);
    assertThat(files.get("categories.csv")).hasSize(3);
    assertThat(files.get("payment_methods.csv")).hasSize(1);
    assertThat(files.get("institutions.csv")).hasSize(1);
    assertThat(files.get("accounts.csv")).hasSize(3);
    assertThat(files.get("transactions.csv")).hasSize(3);
    assertThat(files.get("transfers.csv")).hasSize(3);
    assertThat(files.get("budgets.csv")).hasSize(3);
    assertThat(files.get("recurring_templates.csv")).hasSize(3);
    assertThat(files.get("allocation_plan_entries.csv")).hasSize(3);
    assertThat(files.get("investment_categories.csv")).hasSize(1);
    assertThat(files.get("investment_segments.csv")).hasSize(1);
    assertThat(files.get("investment_subcategories.csv")).hasSize(1);
    assertThat(files.get("investment_products.csv")).hasSize(2);
    assertThat(files.get("investment_holdings.csv")).hasSize(2);
    assertThat(files.get("investment_snapshots.csv")).hasSize(3);
  }

  @Test
  void emptyDatabaseStillYieldsFifteenHeaderOnlyFiles() throws IOException {
    DataExportService empty =
        new DataExportService(
            new FakeCategoryRepository(),
            new FakePaymentMethodRepository(),
            new FakeInstitutionRepository(),
            new FakeAccountRepository(),
            new FakeTransactionRepository(),
            new FakeTransferRepository(),
            new FakeBudgetRepository(),
            new FakeBudgetVersionRepository(),
            new FakeRecurringTemplateRepository(),
            new FakeRecurringTemplateVersionRepository(),
            new FakeInvestmentCategoryRepository(),
            new FakeInvestmentSubcategoryRepository(),
            new FakeInvestmentProductRepository(),
            new FakeInvestmentHoldingRepository(),
            new FakeInvestmentSnapshotRepository(),
            new FakeVehicleRepository(),
            new FakeInvestmentSegmentRepository(),
            new FakeAllocationPlanRepository(),
            new FakeAllocationPlanVersionRepository());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    empty.export(ExportFilter.none(), out);
    List<String> names = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
      for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
        names.add(e.getName());
        assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).endsWith("\r\n");
      }
    }
    assertThat(names).containsExactlyElementsOf(FILES);
  }

  @Test
  void foreignKeyColumnsCarryBothIdAndCurrentName() throws IOException {
    var files = export(ExportFilter.none());

    var accountRow =
        files.get("accounts.csv").stream().filter(r -> r.get("name").equals("Main")).findFirst();
    assertThat(accountRow).isPresent();
    assertThat(accountRow.get().get("institution_id")).isEqualTo(inst.toString());
    assertThat(accountRow.get().get("institution_name")).isEqualTo("No institution");

    var t1 =
        files.get("transactions.csv").stream()
            .filter(r -> r.get("description").equals("t1"))
            .findFirst()
            .orElseThrow();
    assertThat(t1.get("category_id")).isEqualTo(food.toString());
    assertThat(t1.get("category_name")).isEqualTo("Food");
    assertThat(t1.get("account_id")).isEqualTo(checking.toString());
    assertThat(t1.get("account_name")).isEqualTo("Main");
    assertThat(t1.get("payment_method_id")).isEqualTo(pm.toString());
    assertThat(t1.get("payment_method_name")).isEqualTo("Debit");
    assertThat(t1.get("type")).isEqualTo("EXPENSE");

    var tr1 =
        files.get("transfers.csv").stream()
            .filter(r -> r.get("description").equals("tr1"))
            .findFirst()
            .orElseThrow();
    assertThat(tr1.get("from_account_name")).isEqualTo("Main");
    assertThat(tr1.get("to_account_name")).isEqualTo("Reserve");
  }

  @Test
  void investmentAccountHasEmptyOpeningFieldsAndPlainTransfersEmptyTradeColumns()
      throws IOException {
    var files = export(ExportFilter.none());

    var brokerRow =
        files.get("accounts.csv").stream()
            .filter(r -> r.get("type").equals("INVESTMENT"))
            .findFirst()
            .orElseThrow();
    assertThat(brokerRow.get("opening_balance")).isEmpty();
    assertThat(brokerRow.get("opening_balance_date")).isEmpty();

    var plain =
        files.get("transfers.csv").stream()
            .filter(r -> r.get("description").equals("tr1"))
            .findFirst()
            .orElseThrow();
    assertThat(plain.get("investment_product_id")).isEmpty();
    assertThat(plain.get("investment_product_name")).isEmpty();
    assertThat(plain.get("quantity")).isEmpty();
    assertThat(plain.get("unit_price")).isEmpty();
    assertThat(plain.get("taxes")).isEmpty();

    var trade =
        files.get("transfers.csv").stream()
            .filter(r -> r.get("description").equals("tr3"))
            .findFirst()
            .orElseThrow();
    assertThat(trade.get("investment_product_id")).isEqualTo(fund.toString());
    assertThat(trade.get("investment_product_name")).isEqualTo("Selic 2029");
    assertThat(trade.get("to_account_name")).isEqualTo("Broker");
    assertThat(trade.get("quantity")).isEqualTo("10.5");
    assertThat(trade.get("unit_price")).isEqualTo("95.00");
    assertThat(trade.get("taxes")).isEqualTo("2.50");
  }

  @Test
  void productsCarryCategoryAndSubcategoryNamesWithSubcategoryEmptyWhenNone() throws IOException {
    var products = export(ExportFilter.none()).get("investment_products.csv");

    var withSub = products.stream().filter(r -> r.get("name").equals("Selic 2029")).findFirst();
    assertThat(withSub.orElseThrow().get("investment_category_name")).isEqualTo("Fixed Income");
    assertThat(withSub.get().get("investment_subcategory_id")).isEqualTo(invSub.toString());
    assertThat(withSub.get().get("investment_subcategory_name")).isEqualTo("Treasury");
    // F026 (ADR 0023): ticker/segment columns.
    assertThat(withSub.get().get("ticker")).isEqualTo("KNRI11");
    assertThat(withSub.get().get("segment_id")).isEqualTo(segment.toString());
    assertThat(withSub.get().get("segment_name")).isEqualTo("Logistica");

    var without = products.stream().filter(r -> r.get("name").equals("Pension")).findFirst();
    assertThat(without.orElseThrow().get("investment_subcategory_id")).isEmpty();
    assertThat(without.get().get("investment_subcategory_name")).isEmpty();
    assertThat(without.get().get("ticker")).isEmpty();
    assertThat(without.get().get("segment_id")).isEmpty();
    assertThat(without.get().get("segment_name")).isEmpty();
  }

  @Test
  void allocationPlanEntriesCarryTheProductNameAndAreDateRangeFiltered() throws IOException {
    var all = export(ExportFilter.none()).get("allocation_plan_entries.csv");
    assertThat(all).hasSize(3);
    var janFund =
        all.stream()
            .filter(
                r ->
                    r.get("effective_from").equals("2026-01")
                        && r.get("investment_product_id").equals(fund.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(janFund.get("investment_product_name")).isEqualTo("Selic 2029");
    assertThat(janFund.get("target_percentage")).isEqualTo("60.00");
    assertThat(janFund.get("plan_id")).isEqualTo(planId.toString());

    var ranged =
        export(
                new ExportFilter(
                    LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"), null, null))
            .get("allocation_plan_entries.csv");
    assertThat(ranged).isEmpty();

    var fromMarch =
        export(new ExportFilter(LocalDate.parse("2026-02-15"), null, null, null))
            .get("allocation_plan_entries.csv");
    assertThat(fromMarch).extracting(r -> r.get("effective_from")).containsOnly("2026-03");
  }

  @Test
  void holdingsCarryTheProductAndAccountNames() throws IOException {
    var holdings = export(ExportFilter.none()).get("investment_holdings.csv");

    var fundRow =
        holdings.stream()
            .filter(r -> r.get("investment_product_name").equals("Selic 2029"))
            .findFirst();
    assertThat(fundRow.orElseThrow().get("account_name")).isEqualTo("Broker");
    assertThat(fundRow.get().get("closed_date")).isEmpty();
  }

  @Test
  void aTombstoneBudgetVersionExportsAnEmptyMonthlyCap() throws IOException {
    Budget rentBudget =
        budgets.findAll().stream()
            .filter(b -> b.getCategoryId().equals(rent))
            .findFirst()
            .orElseThrow();
    budgetVersions.save(
        BudgetVersion.tombstone(UUID.randomUUID(), rentBudget.getId(), YearMonth.parse("2026-04")));

    var rows = export(ExportFilter.none()).get("budgets.csv");

    assertThat(rows).hasSize(4);
    assertThat(rows)
        .filteredOn(r -> r.get("effective_from").equals("2026-04"))
        .singleElement()
        .satisfies(r -> assertThat(r.get("monthly_cap")).isEmpty());
    assertThat(rows)
        .filteredOn(r -> !r.get("effective_from").equals("2026-04"))
        .allSatisfy(r -> assertThat(r.get("monthly_cap")).isNotEmpty());
  }

  @Test
  void snapshotsCarryTheProductAndAccountNames() throws IOException {
    var snapshots = export(ExportFilter.none()).get("investment_snapshots.csv");

    assertThat(snapshots)
        .extracting(r -> r.get("investment_product_name"))
        .containsExactlyInAnyOrder("Selic 2029", "Selic 2029", "Pension");
    assertThat(snapshots).allSatisfy(r -> assertThat(r.get("account_name")).isEqualTo("Broker"));
  }

  @Test
  void versionedFilesHaveOneRowPerVersionWithParentNames() throws IOException {
    var files = export(ExportFilter.none());

    assertThat(column(files, "budgets.csv", "effective_from"))
        .containsExactlyInAnyOrder("2026-01", "2026-02", "2026-03");
    assertThat(column(files, "budgets.csv", "category_name"))
        .containsExactlyInAnyOrder("Food", "Food", "Rent");
    assertThat(column(files, "recurring_templates.csv", "effective_from"))
        .containsExactlyInAnyOrder("2026-01", "2026-02", "2026-03");
    assertThat(column(files, "recurring_templates.csv", "account_name"))
        .containsExactlyInAnyOrder("Main", "Main", "Reserve");
    assertThat(column(files, "recurring_templates.csv", "category_name"))
        .containsExactlyInAnyOrder("Food", "Food", "Rent");
  }

  @Test
  void dateRangeFiltersOnlyItsFiles() throws IOException {
    var files =
        export(
            new ExportFilter(
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"), null, null));

    assertThat(column(files, "transactions.csv", "description")).containsExactly("t2");
    assertThat(column(files, "transfers.csv", "description")).containsExactly("tr2");
    assertThat(column(files, "investment_snapshots.csv", "date")).containsExactly("2026-02-28");
    assertThat(column(files, "budgets.csv", "effective_from")).containsExactly("2026-02");
    assertThat(column(files, "recurring_templates.csv", "effective_from"))
        .containsExactly("2026-02");
    assertThat(files.get("allocation_plan_entries.csv")).isEmpty();
    assertReferenceFilesFull(files);
  }

  @Test
  void dateRangeComparesVersionsByMonthSoAMidMonthBoundStillIncludesThatMonth() throws IOException {
    var files =
        export(
            new ExportFilter(
                LocalDate.parse("2026-02-20"), LocalDate.parse("2026-03-05"), null, null));

    assertThat(column(files, "budgets.csv", "effective_from"))
        .containsExactlyInAnyOrder("2026-02", "2026-03");
  }

  @Test
  void aReversedDateRangeIsRejected() {
    ExportFilter reversed =
        new ExportFilter(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-02-01"), null, null);

    assertThatThrownBy(() -> service.export(reversed, new ByteArrayOutputStream()))
        .isInstanceOf(InvalidExportRangeException.class);
  }

  @Test
  void openEndedDateRangesWork() throws IOException {
    var from = export(new ExportFilter(LocalDate.parse("2026-02-11"), null, null, null));
    assertThat(column(from, "transactions.csv", "description")).containsExactly("t3");

    var to = export(new ExportFilter(null, LocalDate.parse("2026-01-31"), null, null));
    assertThat(column(to, "transactions.csv", "description")).containsExactly("t1");
    assertThat(column(to, "investment_snapshots.csv", "date")).containsExactly("2026-01-31");
  }

  @Test
  void accountFilterMatchesEitherSideOfATransferAndTouchesOnlyItsFiles() throws IOException {
    var files = export(new ExportFilter(null, null, checking, null));

    assertThat(column(files, "transactions.csv", "description")).containsExactly("t1");
    assertThat(column(files, "transfers.csv", "description"))
        .containsExactlyInAnyOrder("tr1", "tr2", "tr3");
    assertThat(column(files, "recurring_templates.csv", "description"))
        .containsExactly("tpl-food", "tpl-food");
    assertThat(files.get("budgets.csv")).hasSize(3);
    assertThat(files.get("investment_snapshots.csv")).hasSize(3);
    // F026 (ADR 0023): allocation_plan_entries isn't account-filtered, stays full.
    assertThat(files.get("allocation_plan_entries.csv")).hasSize(3);
    assertReferenceFilesFull(files);
  }

  @Test
  void categoryFilterTouchesOnlyItsFiles() throws IOException {
    var files = export(new ExportFilter(null, null, null, rent));

    assertThat(files.get("transactions.csv")).isEmpty();
    assertThat(column(files, "budgets.csv", "category_name")).containsExactly("Rent");
    assertThat(column(files, "recurring_templates.csv", "category_name")).containsExactly("Rent");
    assertThat(files.get("transfers.csv")).hasSize(3);
    assertThat(files.get("investment_snapshots.csv")).hasSize(3);
    // F026 (ADR 0023): allocation_plan_entries isn't category-filtered, stays full.
    assertThat(files.get("allocation_plan_entries.csv")).hasSize(3);
    assertReferenceFilesFull(files);
  }

  @Test
  void combinedFiltersAreAnded() throws IOException {
    var files =
        export(
            new ExportFilter(
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"), savings, food));

    assertThat(column(files, "transactions.csv", "description")).containsExactly("t3");
    assertThat(files.get("transfers.csv")).isEmpty();
    assertThat(column(files, "budgets.csv", "effective_from")).containsExactly("2026-03");
    assertThat(files.get("recurring_templates.csv")).isEmpty();
    assertThat(column(files, "allocation_plan_entries.csv", "effective_from"))
        .containsExactly("2026-03");
    assertReferenceFilesFull(files);
  }

  // F024 (ADR 0021): transactions.csv gains vehicle_id/vehicle_name/fuel_type/liters/
  // price_per_liter/km_since_last_fill/odometer, empty for a non-fuel row. A standalone fixture
  // (not the shared one above) so it doesn't perturb every other test's exact row-count/filter
  // assertions on transactions.csv.
  @Test
  void fuelColumnsArePopulatedForFuelRowsAndEmptyForOtherRows() throws IOException {
    FakeCategoryRepository fuelCategories = new FakeCategoryRepository();
    FakeAccountRepository fuelAccounts = new FakeAccountRepository();
    FakeTransactionRepository fuelTransactions = new FakeTransactionRepository();
    FakeVehicleRepository fuelVehicles = new FakeVehicleRepository();
    FakeInstitutionRepository fuelInstitutions = new FakeInstitutionRepository();
    FakePaymentMethodRepository fuelPaymentMethods = new FakePaymentMethodRepository();

    UUID institutionId = UUID.randomUUID();
    fuelInstitutions.save(Institution.reconstitute(institutionId, "No institution", true));
    UUID accountId = UUID.randomUUID();
    fuelAccounts.save(
        AccountMother.checking()
            .withId(accountId)
            .withName("Checking")
            .withInstitutionId(institutionId)
            .build());
    UUID plainCategoryId = UUID.randomUUID();
    fuelCategories.save(Category.create(plainCategoryId, "Groceries", CategoryType.EXPENSE));
    UUID fuelCategoryId = UUID.randomUUID();
    fuelCategories.save(
        Category.reconstitute(fuelCategoryId, "Fuel", CategoryType.EXPENSE, false, true));
    UUID paymentMethodId = UUID.randomUUID();
    fuelPaymentMethods.save(PaymentMethod.create(paymentMethodId, "Debit"));
    UUID vehicleId = UUID.randomUUID();
    fuelVehicles.save(Vehicle.create(vehicleId, "Civic"));

    fuelTransactions.save(
        TransactionMother.expense()
            .withDescription("Groceries")
            .withCategoryId(plainCategoryId)
            .withAccountId(accountId)
            .withPaymentMethodId(paymentMethodId)
            .withDate(LocalDate.parse("2026-01-05"))
            .build());
    fuelTransactions.save(
        TransactionMother.expense()
            .withDescription("Fill up")
            .withCategoryId(fuelCategoryId)
            .withAccountId(accountId)
            .withPaymentMethodId(paymentMethodId)
            .withDate(LocalDate.parse("2026-01-10"))
            .withFuelDetails(
                new FuelDetails(
                    vehicleId,
                    FuelType.GASOLINA,
                    new BigDecimal("40.500"),
                    new BigDecimal("5.799"),
                    new BigDecimal("400.0"),
                    new BigDecimal("12345.0")))
            .build());

    DataExportService fuelService =
        new DataExportService(
            fuelCategories,
            fuelPaymentMethods,
            fuelInstitutions,
            fuelAccounts,
            fuelTransactions,
            new FakeTransferRepository(),
            new FakeBudgetRepository(),
            new FakeBudgetVersionRepository(),
            new FakeRecurringTemplateRepository(),
            new FakeRecurringTemplateVersionRepository(),
            new FakeInvestmentCategoryRepository(),
            new FakeInvestmentSubcategoryRepository(),
            new FakeInvestmentProductRepository(),
            new FakeInvestmentHoldingRepository(),
            new FakeInvestmentSnapshotRepository(),
            fuelVehicles,
            new FakeInvestmentSegmentRepository(),
            new FakeAllocationPlanRepository(),
            new FakeAllocationPlanVersionRepository());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    fuelService.export(ExportFilter.none(), out);
    Map<String, List<Map<String, String>>> files;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
      files = new LinkedHashMap<>();
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        String[] lines = new String(zip.readAllBytes(), StandardCharsets.UTF_8).split("\r\n", -1);
        String[] header = lines[0].split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
          if (lines[i].isEmpty()) {
            continue;
          }
          String[] cells = lines[i].split(",", -1);
          Map<String, String> row = new LinkedHashMap<>();
          for (int c = 0; c < header.length; c++) {
            row.put(header[c], cells[c]);
          }
          rows.add(row);
        }
        files.put(entry.getName(), rows);
      }
    }

    var plainRow =
        files.get("transactions.csv").stream()
            .filter(r -> r.get("description").equals("Groceries"))
            .findFirst()
            .orElseThrow();
    assertThat(plainRow.get("vehicle_id")).isEmpty();
    assertThat(plainRow.get("vehicle_name")).isEmpty();
    assertThat(plainRow.get("fuel_type")).isEmpty();
    assertThat(plainRow.get("liters")).isEmpty();
    assertThat(plainRow.get("price_per_liter")).isEmpty();
    assertThat(plainRow.get("km_since_last_fill")).isEmpty();
    assertThat(plainRow.get("odometer")).isEmpty();
    assertThat(plainRow.get("investment_holding_id")).isEmpty();

    var fuelRow =
        files.get("transactions.csv").stream()
            .filter(r -> r.get("description").equals("Fill up"))
            .findFirst()
            .orElseThrow();
    assertThat(fuelRow.get("vehicle_id")).isEqualTo(vehicleId.toString());
    assertThat(fuelRow.get("vehicle_name")).isEqualTo("Civic");
    assertThat(fuelRow.get("fuel_type")).isEqualTo("GASOLINA");
    assertThat(fuelRow.get("liters")).isEqualTo("40.500");
    assertThat(fuelRow.get("price_per_liter")).isEqualTo("5.799");
    assertThat(fuelRow.get("km_since_last_fill")).isEqualTo("400.0");
    assertThat(fuelRow.get("odometer")).isEqualTo("12345.0");
  }

  private void assertReferenceFilesFull(Map<String, List<Map<String, String>>> files) {
    assertThat(files.get("categories.csv")).hasSize(3);
    assertThat(files.get("payment_methods.csv")).hasSize(1);
    assertThat(files.get("institutions.csv")).hasSize(1);
    assertThat(files.get("accounts.csv")).hasSize(3);
    assertThat(files.get("investment_categories.csv")).hasSize(1);
    assertThat(files.get("investment_segments.csv")).hasSize(1);
    assertThat(files.get("investment_subcategories.csv")).hasSize(1);
    assertThat(files.get("investment_products.csv")).hasSize(2);
    assertThat(files.get("investment_holdings.csv")).hasSize(2);
  }
}
