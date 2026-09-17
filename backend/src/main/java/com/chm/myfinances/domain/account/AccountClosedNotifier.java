package com.chm.myfinances.domain.account;

import java.util.UUID;

/**
 * Port fired when an account is closed (F003 spec). Implemented by F007 (Recurring Templates) -
 * {@code infrastructure/recurringtemplate/RealAccountClosedNotifier} - which auto-deactivates any
 * {@code RecurringTemplate} still pointing at this account, per PRD S5.4 ("Closing an account
 * auto-deactivates ... any RecurringTemplate still pointing at it"). Expressed as a port here -
 * rather than a direct call into F007's package - so F003 doesn't need to know F007's internals
 * (ADR 0004's cross-feature-dependency guidance).
 */
public interface AccountClosedNotifier {

  void accountClosed(UUID accountId);
}
