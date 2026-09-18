package com.chm.myfinances.infrastructure.recurringtemplate;

import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.domain.account.AccountClosedNotifier;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The real {@link AccountClosedNotifier} implementation (F007 spec), replacing F003's placeholder
 * {@code NoOpAccountClosedNotifier} now that this feature exists: deactivates every {@code
 * RecurringTemplate} still pointed at the closed account (PRD S5.4 - "Closing an account
 * auto-deactivates ... any RecurringTemplate still pointing at it"), via {@link
 * RecurringTemplateService#deactivateForAccount}. F003's {@code AccountService} depends only on the
 * {@code domain.account.AccountClosedNotifier} port, never on this class directly - it doesn't need
 * to know F007 exists (ADR 0004's cross-feature-dependency guidance).
 */
@Component
public class RealAccountClosedNotifier implements AccountClosedNotifier {

  private final RecurringTemplateService recurringTemplateService;

  public RealAccountClosedNotifier(RecurringTemplateService recurringTemplateService) {
    this.recurringTemplateService = recurringTemplateService;
  }

  @Override
  public void accountClosed(UUID accountId) {
    recurringTemplateService.deactivateForAccount(accountId);
  }
}
