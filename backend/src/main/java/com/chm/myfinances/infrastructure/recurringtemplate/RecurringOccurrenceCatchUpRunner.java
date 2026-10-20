package com.chm.myfinances.infrastructure.recurringtemplate;

import com.chm.myfinances.application.recurringtemplate.RecurringOccurrenceCatchUpService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs F007's lazy/catch-up pending-occurrence generation once at backend startup (F007 spec: "run
 * at backend startup and before any request that reads recurring data") - the other trigger,
 * "before any request that reads recurring data", is {@code
 * RecurringTemplateService.findAllPending()} calling the same {@link
 * RecurringOccurrenceCatchUpService} directly. This runner exists only so a template that's already
 * due doesn't wait for the first dashboard load after a long time away (PRD S7.3's on-demand
 * runtime model - the app may have been off for months).
 */
@Component
public class RecurringOccurrenceCatchUpRunner implements ApplicationRunner {

  private final RecurringOccurrenceCatchUpService catchUpService;

  public RecurringOccurrenceCatchUpRunner(RecurringOccurrenceCatchUpService catchUpService) {
    this.catchUpService = catchUpService;
  }

  @Override
  public void run(ApplicationArguments args) {
    catchUpService.runCatchUp();
  }
}
