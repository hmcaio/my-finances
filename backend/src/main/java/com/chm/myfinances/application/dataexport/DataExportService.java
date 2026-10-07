package com.chm.myfinances.application.dataexport;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data export (F013, PRD S6.9): one CSV per entity, zipped into a single stream. Every foreign key
 * is written as an {@code _id} column followed by an {@code _name} column holding the referenced
 * entity's current name, so each file is usable in a spreadsheet without joins.
 *
 * <p>Filter applicability, exactly as the PRD table: <b>date range</b> - transactions, transfers,
 * investment snapshots, and budget / recurring-template versions (by {@code effective_from},
 * compared by month: a version is kept when its month lies within {@code [month(dateFrom),
 * month(dateTo)]}); <b>account</b> - transactions, transfers (either side), recurring templates;
 * <b>category</b> - transactions, budgets, recurring templates. Categories, payment methods,
 * institutions, accounts and the investment reference files are always complete. Filters combine
 * with AND.
 *
 * <p>Read-only and {@code @Transactional(readOnly = true)} so every file is read from one
 * consistent snapshot. Rows are sorted (date then id for the dated files, name for reference files)
 * so the output is deterministic. Logs counts only (ADR 0011).
 */
@Service
public class DataExportService {

  private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

  private final CategoryRepository categories;
  private final PaymentMethodRepository paymentMethods;
  private final InstitutionRepository institutions;
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final TransferRepository transfers;
  private final BudgetRepository budgets;
  private final BudgetVersionRepository budgetVersions;
  private final RecurringTemplateRepository templates;
  private final RecurringTemplateVersionRepository templateVersions;
  private final InvestmentCategoryRepository investmentCategories;
  private final InvestmentSubcategoryRepository investmentSubcategories;
  private final InvestmentProductRepository investmentProducts;
  private final InvestmentHoldingRepository investmentHoldings;
  private final InvestmentSnapshotRepository investmentSnapshots;
  private final VehicleRepository vehicles;

  public DataExportService(
      CategoryRepository categories,
      PaymentMethodRepository paymentMethods,
      InstitutionRepository institutions,
      AccountRepository accounts,
      TransactionRepository transactions,
      TransferRepository transfers,
      BudgetRepository budgets,
      BudgetVersionRepository budgetVersions,
      RecurringTemplateRepository templates,
      RecurringTemplateVersionRepository templateVersions,
      InvestmentCategoryRepository investmentCategories,
      InvestmentSubcategoryRepository investmentSubcategories,
      InvestmentProductRepository investmentProducts,
      InvestmentHoldingRepository investmentHoldings,
      InvestmentSnapshotRepository investmentSnapshots,
      VehicleRepository vehicles) {
    this.categories = categories;
    this.paymentMethods = paymentMethods;
    this.institutions = institutions;
    this.accounts = accounts;
    this.transactions = transactions;
    this.transfers = transfers;
    this.budgets = budgets;
    this.budgetVersions = budgetVersions;
    this.templates = templates;
    this.templateVersions = templateVersions;
    this.investmentCategories = investmentCategories;
    this.investmentSubcategories = investmentSubcategories;
    this.investmentProducts = investmentProducts;
    this.investmentHoldings = investmentHoldings;
    this.investmentSnapshots = investmentSnapshots;
    this.vehicles = vehicles;
  }

  /** Writes the thirteen CSVs, zipped, to {@code out} (which is left open for the caller). */
  @Transactional(readOnly = true)
  public void export(ExportFilter filter, OutputStream out) throws IOException {
    if (filter.dateFrom() != null
        && filter.dateTo() != null
        && filter.dateFrom().isAfter(filter.dateTo())) {
      throw new InvalidExportRangeException();
    }
    Lookups names = new Lookups();
    ZipOutputStream zip = new ZipOutputStream(out);
    writeCategories(zip);
    writePaymentMethods(zip);
    writeInstitutions(zip);
    writeAccounts(zip, names);
    writeTransactions(zip, filter, names);
    writeTransfers(zip, filter, names);
    writeBudgets(zip, filter, names);
    writeRecurringTemplates(zip, filter, names);
    writeInvestmentCategories(zip);
    writeInvestmentSubcategories(zip, names);
    writeInvestmentProducts(zip, names);
    writeInvestmentHoldings(zip, names);
    writeInvestmentSnapshots(zip, filter, names);
    zip.finish();
    log.info(
        "Data export written (dateFilter={}, accountFilter={}, categoryFilter={})",
        filter.dateFrom() != null || filter.dateTo() != null,
        filter.accountId() != null,
        filter.categoryId() != null);
  }

  /** Name lookups for the FK columns, loaded once per export. */
  private final class Lookups {
    final Map<UUID, String> category =
        names(categories.findAll(), Category::getId, Category::getName);
    final Map<UUID, String> paymentMethod =
        names(paymentMethods.findAll(), PaymentMethod::getId, PaymentMethod::getName);
    final Map<UUID, String> institution =
        names(institutions.findAll(), Institution::getId, Institution::getName);
    final Map<UUID, String> account = names(accounts.findAll(), Account::getId, Account::getName);
    final Map<UUID, String> investmentCategory =
        names(
            investmentCategories.findAll(), InvestmentCategory::getId, InvestmentCategory::getName);
    final Map<UUID, String> investmentSubcategory =
        names(
            investmentSubcategories.findAll(),
            InvestmentSubcategory::getId,
            InvestmentSubcategory::getName);
    final Map<UUID, String> investmentProduct =
        names(investmentProducts.findAll(), InvestmentProduct::getId, InvestmentProduct::getName);
    final Map<UUID, InvestmentHolding> investmentHoldingById = byId(investmentHoldings.findAll());
    final Map<UUID, String> vehicle = names(vehicles.findAll(), Vehicle::getId, Vehicle::getName);
  }

  private static Map<UUID, InvestmentHolding> byId(List<InvestmentHolding> holdings) {
    Map<UUID, InvestmentHolding> map = new HashMap<>();
    holdings.forEach(h -> map.put(h.getId(), h));
    return map;
  }

  private static <T> Map<UUID, String> names(
      List<T> rows, Function<T, UUID> id, Function<T, String> name) {
    Map<UUID, String> map = new HashMap<>();
    rows.forEach(r -> map.put(id.apply(r), name.apply(r)));
    return map;
  }

  /** The referenced entity's current name, or empty for an absent (null) reference. */
  private static String nameOf(Map<UUID, String> names, UUID id) {
    return id == null ? "" : names.getOrDefault(id, "");
  }

  private static CsvWriter begin(ZipOutputStream zip, String file, String... header)
      throws IOException {
    zip.putNextEntry(new ZipEntry(file));
    CsvWriter csv = new CsvWriter(zip);
    csv.row((Object[]) header);
    return csv;
  }

  private static void end(ZipOutputStream zip, CsvWriter csv) throws IOException {
    csv.flush();
    zip.closeEntry();
  }

  private void writeCategories(ZipOutputStream zip) throws IOException {
    CsvWriter csv = begin(zip, "categories.csv", "id", "name", "type", "built_in");
    for (Category c : sorted(categories.findAll(), Category::getName, Category::getId)) {
      csv.row(c.getId(), c.getName(), c.getType(), c.isBuiltIn());
    }
    end(zip, csv);
  }

  private void writePaymentMethods(ZipOutputStream zip) throws IOException {
    CsvWriter csv = begin(zip, "payment_methods.csv", "id", "name");
    for (PaymentMethod p :
        sorted(paymentMethods.findAll(), PaymentMethod::getName, PaymentMethod::getId)) {
      csv.row(p.getId(), p.getName());
    }
    end(zip, csv);
  }

  private void writeInstitutions(ZipOutputStream zip) throws IOException {
    CsvWriter csv = begin(zip, "institutions.csv", "id", "name", "built_in");
    for (Institution i : sorted(institutions.findAll(), Institution::getName, Institution::getId)) {
      csv.row(i.getId(), i.getName(), i.isBuiltIn());
    }
    end(zip, csv);
  }

  private void writeAccounts(ZipOutputStream zip, Lookups n) throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "accounts.csv",
            "id",
            "name",
            "type",
            "institution_id",
            "institution_name",
            "opening_balance",
            "opening_balance_date",
            "closed_date");
    for (Account a : sorted(accounts.findAll(), Account::getName, Account::getId)) {
      // INVESTMENT accounts have no opening balance/date (null -> empty cells, ADR 0012).
      csv.row(
          a.getId(),
          a.getName(),
          a.getType(),
          a.getInstitutionId(),
          nameOf(n.institution, a.getInstitutionId()),
          a.getOpeningBalance(),
          a.getOpeningBalanceDate(),
          a.getClosedDate());
    }
    end(zip, csv);
  }

  private void writeTransactions(ZipOutputStream zip, ExportFilter f, Lookups n)
      throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "transactions.csv",
            "id",
            "date",
            "amount",
            "type",
            "category_id",
            "category_name",
            "account_id",
            "account_name",
            "payment_method_id",
            "payment_method_name",
            "recurring_template_version_id",
            "description",
            "additional_notes",
            "vehicle_id",
            "vehicle_name",
            "fuel_type",
            "liters",
            "price_per_liter",
            "km_since_last_fill",
            "odometer");
    TransactionFilter filter =
        new TransactionFilter(f.dateFrom(), f.dateTo(), f.categoryId(), f.accountId(), null);
    List<Transaction> rows =
        sorted(
            transactions.findAll(filter, Pageable.unpaged()).getContent(),
            Transaction::getDate,
            Transaction::getId);
    for (Transaction t : rows) {
      // F024 (ADR 0021): the fuel columns are empty for a non-fuel transaction (fuelDetails null).
      FuelDetails fuel = t.getFuelDetails();
      csv.row(
          t.getId(),
          t.getDate(),
          t.getAmount(),
          t.getType(),
          t.getCategoryId(),
          nameOf(n.category, t.getCategoryId()),
          t.getAccountId(),
          nameOf(n.account, t.getAccountId()),
          t.getPaymentMethodId(),
          nameOf(n.paymentMethod, t.getPaymentMethodId()),
          t.getRecurringTemplateVersionId(),
          t.getDescription(),
          t.getAdditionalNotes(),
          fuel == null ? null : fuel.vehicleId(),
          fuel == null ? "" : nameOf(n.vehicle, fuel.vehicleId()),
          fuel == null ? null : fuel.fuelType(),
          fuel == null ? null : fuel.liters(),
          fuel == null ? null : fuel.pricePerLiter(),
          fuel == null ? null : fuel.kmSinceLastFill(),
          fuel == null ? null : fuel.odometer());
    }
    end(zip, csv);
  }

  private void writeTransfers(ZipOutputStream zip, ExportFilter f, Lookups n) throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "transfers.csv",
            "id",
            "date",
            "from_account_id",
            "from_account_name",
            "to_account_id",
            "to_account_name",
            "amount",
            "description",
            "additional_notes",
            "investment_product_id",
            "investment_product_name",
            "quantity",
            "unit_price",
            "taxes");
    TransferFilter filter = new TransferFilter(f.dateFrom(), f.dateTo(), f.accountId(), null);
    List<Transfer> rows =
        sorted(
            transfers.findAll(filter, Pageable.unpaged()).getContent(),
            Transfer::getDate,
            Transfer::getId);
    for (Transfer t : rows) {
      InvestmentTradeDetails trade = t.getTradeDetails();
      boolean hasTrade = trade != null;
      csv.row(
          t.getId(),
          t.getDate(),
          t.getFromAccountId(),
          nameOf(n.account, t.getFromAccountId()),
          t.getToAccountId(),
          nameOf(n.account, t.getToAccountId()),
          t.getAmount(),
          t.getDescription(),
          t.getAdditionalNotes(),
          t.getInvestmentProductId(),
          nameOf(n.investmentProduct, t.getInvestmentProductId()),
          hasTrade ? trade.quantity() : null,
          hasTrade ? trade.unitPrice() : null,
          hasTrade ? trade.taxes() : null);
    }
    end(zip, csv);
  }

  private void writeBudgets(ZipOutputStream zip, ExportFilter f, Lookups n) throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "budgets.csv",
            "budget_id",
            "version_id",
            "category_id",
            "category_name",
            "monthly_cap",
            "effective_from");
    for (Budget b : sorted(budgets.findAll(), Budget::getCategoryId, Budget::getId)) {
      if (f.categoryId() != null && !f.categoryId().equals(b.getCategoryId())) {
        continue;
      }
      for (BudgetVersion v :
          sorted(
              budgetVersions.findByBudgetId(b.getId()),
              BudgetVersion::getEffectiveFrom,
              BudgetVersion::getId)) {
        if (!inMonthRange(v.getEffectiveFrom(), f)) {
          continue;
        }
        csv.row(
            b.getId(),
            v.getId(),
            b.getCategoryId(),
            nameOf(n.category, b.getCategoryId()),
            v.getMonthlyCap(),
            v.getEffectiveFrom());
      }
    }
    end(zip, csv);
  }

  private void writeRecurringTemplates(ZipOutputStream zip, ExportFilter f, Lookups n)
      throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "recurring_templates.csv",
            "template_id",
            "version_id",
            "category_id",
            "category_name",
            "account_id",
            "account_name",
            "description",
            "active",
            "last_generated_for",
            "amount",
            "day_of_month",
            "effective_from");
    for (RecurringTemplate t :
        sorted(templates.findAll(), RecurringTemplate::getDescription, RecurringTemplate::getId)) {
      if (f.categoryId() != null && !f.categoryId().equals(t.getCategoryId())) {
        continue;
      }
      if (f.accountId() != null && !f.accountId().equals(t.getAccountId())) {
        continue;
      }
      for (RecurringTemplateVersion v :
          sorted(
              templateVersions.findByTemplateId(t.getId()),
              RecurringTemplateVersion::getEffectiveFrom,
              RecurringTemplateVersion::getId)) {
        if (!inMonthRange(v.getEffectiveFrom(), f)) {
          continue;
        }
        csv.row(
            t.getId(),
            v.getId(),
            t.getCategoryId(),
            nameOf(n.category, t.getCategoryId()),
            t.getAccountId(),
            nameOf(n.account, t.getAccountId()),
            t.getDescription(),
            t.isActive(),
            t.getLastGeneratedFor(),
            v.getAmount(),
            v.getDayOfMonth(),
            v.getEffectiveFrom());
      }
    }
    end(zip, csv);
  }

  private void writeInvestmentCategories(ZipOutputStream zip) throws IOException {
    CsvWriter csv = begin(zip, "investment_categories.csv", "id", "name");
    for (InvestmentCategory c :
        sorted(
            investmentCategories.findAll(),
            InvestmentCategory::getName,
            InvestmentCategory::getId)) {
      csv.row(c.getId(), c.getName());
    }
    end(zip, csv);
  }

  private void writeInvestmentSubcategories(ZipOutputStream zip, Lookups n) throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "investment_subcategories.csv",
            "id",
            "investment_category_id",
            "investment_category_name",
            "name");
    for (InvestmentSubcategory s :
        sorted(
            investmentSubcategories.findAll(),
            InvestmentSubcategory::getName,
            InvestmentSubcategory::getId)) {
      csv.row(
          s.getId(),
          s.getInvestmentCategoryId(),
          nameOf(n.investmentCategory, s.getInvestmentCategoryId()),
          s.getName());
    }
    end(zip, csv);
  }

  private void writeInvestmentProducts(ZipOutputStream zip, Lookups n) throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "investment_products.csv",
            "id",
            "investment_category_id",
            "investment_category_name",
            "investment_subcategory_id",
            "investment_subcategory_name",
            "name",
            "additional_notes");
    for (InvestmentProduct p :
        sorted(
            investmentProducts.findAll(), InvestmentProduct::getName, InvestmentProduct::getId)) {
      csv.row(
          p.getId(),
          p.getInvestmentCategoryId(),
          nameOf(n.investmentCategory, p.getInvestmentCategoryId()),
          p.getInvestmentSubcategoryId(),
          nameOf(n.investmentSubcategory, p.getInvestmentSubcategoryId()),
          p.getName(),
          p.getAdditionalNotes());
    }
    end(zip, csv);
  }

  /**
   * {@code InvestmentHolding} (F022, ADR 0020): the many-to-many link a product/account pair now
   * goes through, in place of the old {@code investment_products.account_id}/{@code closed_date}.
   */
  private void writeInvestmentHoldings(ZipOutputStream zip, Lookups n) throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "investment_holdings.csv",
            "id",
            "investment_product_id",
            "investment_product_name",
            "account_id",
            "account_name",
            "closed_date",
            "additional_notes");
    for (InvestmentHolding h :
        sorted(
            investmentHoldings.findAll(),
            h -> nameOf(n.investmentProduct, h.getProductId()),
            InvestmentHolding::getId)) {
      csv.row(
          h.getId(),
          h.getProductId(),
          nameOf(n.investmentProduct, h.getProductId()),
          h.getAccountId(),
          nameOf(n.account, h.getAccountId()),
          h.getClosedDate(),
          h.getAdditionalNotes());
    }
    end(zip, csv);
  }

  private void writeInvestmentSnapshots(ZipOutputStream zip, ExportFilter f, Lookups n)
      throws IOException {
    CsvWriter csv =
        begin(
            zip,
            "investment_snapshots.csv",
            "id",
            "investment_holding_id",
            "investment_product_name",
            "account_name",
            "date",
            "balance");
    for (InvestmentSnapshot s :
        sorted(
            investmentSnapshots.findAll(),
            InvestmentSnapshot::getDate,
            InvestmentSnapshot::getId)) {
      if ((f.dateFrom() != null && s.getDate().isBefore(f.dateFrom()))
          || (f.dateTo() != null && s.getDate().isAfter(f.dateTo()))) {
        continue;
      }
      InvestmentHolding holding = n.investmentHoldingById.get(s.getHoldingId());
      csv.row(
          s.getId(),
          s.getHoldingId(),
          holding == null ? "" : nameOf(n.investmentProduct, holding.getProductId()),
          holding == null ? "" : nameOf(n.account, holding.getAccountId()),
          s.getDate(),
          s.getBalance());
    }
    end(zip, csv);
  }

  private static boolean inMonthRange(YearMonth month, ExportFilter f) {
    return (f.dateFrom() == null || !month.isBefore(YearMonth.from(f.dateFrom())))
        && (f.dateTo() == null || !month.isAfter(YearMonth.from(f.dateTo())));
  }

  private static <T, K extends Comparable<? super K>, I extends Comparable<? super I>>
      List<T> sorted(List<T> rows, Function<T, K> key, Function<T, I> id) {
    return rows.stream().sorted(Comparator.comparing(key).thenComparing(id)).toList();
  }
}
