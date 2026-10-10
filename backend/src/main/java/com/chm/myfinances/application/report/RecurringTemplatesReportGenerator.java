package com.chm.myfinances.application.report;

import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Report 8 (F030 spec): every currently-active {@link RecurringTemplate}, as of a selectable date
 * (defaults to today) - category, account, amount, day-of-month, next occurrence. No history, no
 * inactive templates - a "what am I committed to right now" view.
 */
@Service
public class RecurringTemplatesReportGenerator implements ReportGenerator {

  private static final List<String> COLUMNS =
      List.of("category_name", "account_name", "amount", "day_of_month", "next_occurrence");

  private final RecurringTemplateService templateService;
  private final RecurringTemplateVersionRepository versionRepository;
  private final CategoryRepository categoryRepository;
  private final AccountRepository accountRepository;
  private final Clock clock;

  public RecurringTemplatesReportGenerator(
      RecurringTemplateService templateService,
      RecurringTemplateVersionRepository versionRepository,
      CategoryRepository categoryRepository,
      AccountRepository accountRepository,
      Clock clock) {
    this.templateService = templateService;
    this.versionRepository = versionRepository;
    this.categoryRepository = categoryRepository;
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.RECURRING_TEMPLATES;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    LocalDate asOf = filter.asOfDate() != null ? filter.asOfDate() : LocalDate.now(clock);

    Map<UUID, String> categoryNames = new HashMap<>();
    for (Category category : categoryRepository.findAll()) {
      categoryNames.put(category.getId(), category.getName());
    }
    Map<UUID, String> accountNames = new HashMap<>();
    for (Account account : accountRepository.findAll()) {
      accountNames.put(account.getId(), account.getName());
    }

    List<List<Object>> rows = new ArrayList<>();
    List<RecurringTemplate> templates =
        templateService.findAll().stream()
            .filter(RecurringTemplate::isActive)
            .sorted(Comparator.comparing(RecurringTemplate::getDescription))
            .toList();
    for (RecurringTemplate template : templates) {
      List<RecurringTemplateVersion> versions =
          versionRepository.findByTemplateId(template.getId());
      NextOccurrence next = nextOccurrence(versions, asOf);
      if (next == null) {
        continue;
      }
      rows.add(
          Arrays.asList(
              categoryNames.getOrDefault(template.getCategoryId(), ""),
              accountNames.getOrDefault(template.getAccountId(), ""),
              next.version().getAmount(),
              next.version().getDayOfMonth(),
              next.dueDate()));
    }

    byte[] csv = CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, rows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }

  private record NextOccurrence(RecurringTemplateVersion version, LocalDate dueDate) {}

  /**
   * The next occurrence due on or after {@code asOf}: resolves the version effective for {@code
   * asOf}'s own cycle first; if that cycle's due date has already passed, moves to the following
   * cycle's effective version. {@code null} if no version covers either cycle (shouldn't happen for
   * a template with at least one version, but defends against an edge case rather than throwing
   * from a read-only report).
   */
  private static NextOccurrence nextOccurrence(
      List<RecurringTemplateVersion> versions, LocalDate asOf) {
    YearMonth cycle = YearMonth.from(asOf);
    var effective = RecurringTemplateVersion.resolveEffective(versions, cycle);
    if (effective.isEmpty()) {
      return null;
    }
    LocalDate due = effective.get().dueDateFor(cycle);
    if (!due.isBefore(asOf)) {
      return new NextOccurrence(effective.get(), due);
    }
    YearMonth nextCycle = cycle.plusMonths(1);
    var nextEffective = RecurringTemplateVersion.resolveEffective(versions, nextCycle);
    if (nextEffective.isEmpty()) {
      return null;
    }
    return new NextOccurrence(nextEffective.get(), nextEffective.get().dueDateFor(nextCycle));
  }
}
