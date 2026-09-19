package com.chm.myfinances.application.recurringtemplate;

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
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only place F007's lazy/catch-up generation touches persistence (PRD S5.7, F007 spec): for
 * every {@code active} {@code RecurringTemplate}, delegates to the pure {@link
 * RecurringOccurrenceGenerator} to compute which cycles are now due, persists a {@link
 * PendingRecurringOccurrence} for each (guarded by {@code existsByTemplateIdAndDueDate} as defense
 * in depth against double-generating a cycle), and advances the template's {@code
 * lastGeneratedFor}.
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

  public RecurringOccurrenceCatchUpService(
      RecurringTemplateRepository templateRepository,
      RecurringTemplateVersionRepository versionRepository,
      PendingRecurringOccurrenceRepository pendingRepository,
      IdGenerator idGenerator) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.pendingRepository = pendingRepository;
    this.idGenerator = idGenerator;
  }

  /** Runs catch-up generation for every active template, as of today. */
  public void runCatchUp() {
    runCatchUp(LocalDate.now());
  }

  /** Same as {@link #runCatchUp()}, but with an explicit "today" - exposed for testability. */
  public void runCatchUp(LocalDate today) {
    int generatedOccurrences = 0;
    int templatesWithNewOccurrences = 0;
    for (RecurringTemplate template : templateRepository.findAllActive()) {
      int generatedForTemplate = 0;
      List<RecurringTemplateVersion> versions =
          versionRepository.findByTemplateId(template.getId());
      CatchUpResult result =
          RecurringOccurrenceGenerator.catchUp(
              template.isActive(), versions, template.getLastGeneratedFor(), today);

      for (PendingCycle cycle : result.cyclesToGenerate()) {
        if (!pendingRepository.existsByTemplateIdAndDueDate(template.getId(), cycle.dueDate())) {
          pendingRepository.save(
              PendingRecurringOccurrence.create(
                  idGenerator.newId(), template.getId(), cycle.version().getId(), cycle.dueDate()));
          generatedForTemplate++;
        }
      }
      generatedOccurrences += generatedForTemplate;
      if (generatedForTemplate > 0) {
        templatesWithNewOccurrences++;
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
}
