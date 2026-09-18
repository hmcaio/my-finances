package com.chm.myfinances.infrastructure.web.recurringtemplate;

import com.chm.myfinances.application.recurringtemplate.ConfirmOccurrenceOverrides;
import com.chm.myfinances.application.recurringtemplate.RecurringTemplateCurrentVersionQuery;
import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.infrastructure.web.transaction.TransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code RecurringTemplate}/{@code PendingRecurringOccurrence} (F007 spec). Both
 * {@code GET /api/recurring-templates} and {@code GET /api/recurring-templates/pending} are plain
 * lists, not {@code PagedModel} - departing from F004/F005's paginated-list convention the same way
 * F006's {@code GET /api/budgets} does: a single-user household's recurring templates, and its
 * currently-outstanding pending occurrences, are both inherently small in number (there's no
 * unbounded historical accumulation the way transactions/transfers have - a confirmed or dismissed
 * occurrence is deleted, not kept).
 */
@RestController
@RequestMapping("/api/recurring-templates")
public class RecurringTemplateController {

  private final RecurringTemplateService recurringTemplateService;
  private final RecurringTemplateCurrentVersionQuery currentVersionQuery;

  public RecurringTemplateController(
      RecurringTemplateService recurringTemplateService,
      RecurringTemplateCurrentVersionQuery currentVersionQuery) {
    this.recurringTemplateService = recurringTemplateService;
    this.currentVersionQuery = currentVersionQuery;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RecurringTemplateResponse create(
      @Valid @RequestBody CreateRecurringTemplateRequest request) {
    RecurringTemplate template =
        recurringTemplateService.create(
            request.categoryId(),
            request.accountId(),
            request.description(),
            request.amount(),
            request.dayOfMonth(),
            request.effectiveFrom());
    return toResponse(template);
  }

  @GetMapping
  public List<RecurringTemplateResponse> list() {
    return recurringTemplateService.findAll().stream().map(this::toResponse).toList();
  }

  @PatchMapping("/{id}/cap")
  public RecurringTemplateResponse setCap(
      @PathVariable UUID id, @Valid @RequestBody UpdateRecurringTemplateCapRequest request) {
    recurringTemplateService.setCap(
        id, request.amount(), request.dayOfMonth(), request.effectiveFrom());
    return toResponse(recurringTemplateService.findById(id));
  }

  @PostMapping("/{id}/stop")
  public RecurringTemplateResponse stop(@PathVariable UUID id) {
    return toResponse(recurringTemplateService.stop(id));
  }

  @PostMapping("/{id}/reactivate")
  public RecurringTemplateResponse reactivate(@PathVariable UUID id) {
    return toResponse(recurringTemplateService.reactivate(id));
  }

  /** Pending occurrences awaiting confirmation, dashboard-ready (F007 spec, F012 dependency). */
  @GetMapping("/pending")
  public List<PendingRecurringOccurrenceResponse> pending() {
    return recurringTemplateService.findAllPending().stream().map(this::toResponse).toList();
  }

  @PostMapping("/pending/{id}/confirm")
  public TransactionResponse confirm(
      @PathVariable UUID id, @Valid @RequestBody ConfirmPendingOccurrenceRequest request) {
    Transaction transaction =
        recurringTemplateService.confirmPending(
            id,
            new ConfirmOccurrenceOverrides(
                request.amount(),
                request.date(),
                request.accountId(),
                request.paymentMethodId(),
                request.description(),
                request.additionalNotes()));
    return TransactionResponse.from(transaction);
  }

  @DeleteMapping("/pending/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dismiss(@PathVariable UUID id) {
    recurringTemplateService.dismissPending(id);
  }

  private RecurringTemplateResponse toResponse(RecurringTemplate template) {
    Optional<RecurringTemplateVersion> currentVersion =
        currentVersionQuery.currentVersion(template.getId());
    return RecurringTemplateResponse.from(template, currentVersion);
  }

  private PendingRecurringOccurrenceResponse toResponse(PendingRecurringOccurrence occurrence) {
    RecurringTemplateVersion version =
        recurringTemplateService.findVersionById(occurrence.getTemplateVersionId());
    return PendingRecurringOccurrenceResponse.from(occurrence, version);
  }
}
