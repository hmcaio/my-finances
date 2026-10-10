package com.chm.myfinances.application.recurringtemplate;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringOccurrenceGenerator;
import com.chm.myfinances.domain.recurringtemplate.RecurringOccurrenceGenerator.CatchUpResult;
import com.chm.myfinances.domain.recurringtemplate.RecurringOccurrenceGenerator.PendingCycle;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only place F007's lazy/catch-up generation touches persistence (PRD S5.7, F007 spec): for
 * every {@code active} {@code RecurringTemplate}, delegates to the pure {@link
 * RecurringOccurrenceGenerator} to compute which cycles are now due, persists a {@link
 * PendingRecurringOccurrence} for each (via the atomic {@code insertIfAbsent}, backed by a {@code
 * UNIQUE (template_id, due_date)} constraint, so overlapping runs can't double-generate a cycle),
 * and advances the template's {@code lastGeneratedFor}.
 *
 * <p>Run at backend startup ({@code RecurringOccurrenceCatchUpRunner}) and again lazily before
 * {@code RecurringTemplateService.findAllPending()} serves the dashboard's pending-occurrences list
 * (F007 spec: "run at backend startup and before any request that reads recurring data").
 */
@Service
public class RecurringOccurrenceCatchUpService {

  private static final Logger log =
      LoggerFactory.getLogger(RecurringOccurrenceCatchUpService.class);

  private final RecurringTemplateRepository templateRepository;
  private final RecurringTemplateVersionRepository versionRepository;
  private final PendingRecurringOccurrenceRepository pendingRepository;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final AuditRecorder auditRecorder;

  public RecurringOccurrenceCatchUpService(
      RecurringTemplateRepository templateRepository,
      RecurringTemplateVersionRepository versionRepository,
      PendingRecurringOccurrenceRepository pendingRepository,
      IdGenerator idGenerator,
      Clock clock,
      AuditRecorder auditRecorder) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.pendingRepository = pendingRepository;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.auditRecorder = auditRecorder;
  }

  /** Runs catch-up generation for every active template, as of today. */
  public void runCatchUp() {
    runCatchUp(LocalDate.now(clock));
  }

  /** Same as {@link #runCatchUp()}, but with an explicit "today" - exposed for testability. */
  public void runCatchUp(LocalDate today) {
    int generatedOccurrences = 0;
    int templatesWithNewOccurrences = 0;
    for (RecurringTemplate template : templateRepository.findAllActive()) {
      int generatedForTemplate = 0;
      List<LocalDate> generatedDates = new ArrayList<>();
      List<RecurringTemplateVersion> versions =
          versionRepository.findByTemplateId(template.getId());
      CatchUpResult result =
          RecurringOccurrenceGenerator.catchUp(
              template.isActive(), versions, template.getLastGeneratedFor(), today);

      for (PendingCycle cycle : result.cyclesToGenerate()) {
        // Atomic, so a concurrent run generating the same cycle can't produce a duplicate; only
        // the run that actually inserted the row counts it (issue #20).
        boolean inserted =
            pendingRepository.insertIfAbsent(
                PendingRecurringOccurrence.create(
                    idGenerator.newId(),
                    template.getId(),
                    cycle.version().getId(),
                    cycle.dueDate()));
        if (inserted) {
          generatedForTemplate++;
          generatedDates.add(cycle.dueDate());
        }
      }
      generatedOccurrences += generatedForTemplate;
      if (generatedForTemplate > 0) {
        templatesWithNewOccurrences++;
        recordGeneratedSummary(template, generatedDates);
      }

      if (result.advancedLastGeneratedFor() != null
          && !result.advancedLastGeneratedFor().equals(template.getLastGeneratedFor())) {
        template.advanceLastGeneratedFor(result.advancedLastGeneratedFor());
        templateRepository.save(template);
      }
    }

    // Runs before every pending-list request, so a "nothing new" run must not be an INFO line.
    // Counts only - never template descriptions or amounts (F016).
    if (generatedOccurrences > 0) {
      log.info(
          "Recurring catch-up generated {} pending occurrence(s) across {} template(s)",
          generatedOccurrences,
          templatesWithNewOccurrences);
    } else {
      log.debug("Recurring catch-up generated 0 pending occurrence(s)");
    }
  }

  /**
   * One {@code GENERATED} summary entry per template per run (PRD S5.12, ADR 0022) - never one
   * entry per occurrence. Deliberately not wrapped in the same transaction as the occurrences it
   * summarizes: this whole method is intentionally not {@code @Transactional} (a broken template
   * must not roll back every other template's catch-up, backend {@code CLAUDE.md}), so the audit
   * write for one template's summary follows the same per-template, not-all-or-nothing shape.
   */
  private void recordGeneratedSummary(RecurringTemplate template, List<LocalDate> generatedDates) {
    LocalDate first = generatedDates.stream().min(Comparator.naturalOrder()).orElseThrow();
    LocalDate last = generatedDates.stream().max(Comparator.naturalOrder()).orElseThrow();
    Map<String, Object> summary =
        Map.of(
            "count", generatedDates.size(),
            "firstDate", first.toString(),
            "lastDate", last.toString());
    auditRecorder.recordGenerated(
        AuditEntityType.RECURRING_TEMPLATE, template.getId(), template.getDescription(), summary);
  }
}
