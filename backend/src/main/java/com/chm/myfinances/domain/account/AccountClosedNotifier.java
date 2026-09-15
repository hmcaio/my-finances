package com.chm.myfinances.domain.account;

import java.util.UUID;

/**
 * Port fired when an account is closed (F003 spec). F007 (Recurring Templates) will provide the
 * real implementation that auto-deactivates any {@code RecurringTemplate} still pointing at this
 * account, per PRD S5.4 ("Closing an account auto-deactivates ... any RecurringTemplate still
 * pointing at it"). Expressed as a port here - rather than a direct call into F007's package - so
 * F003 doesn't need to know F007's internals (ADR 0004's cross-feature-dependency guidance).
 *
 * <p>Until F007 exists, the only implementation is a no-op ({@code
 * infrastructure/account/NoOpAccountClosedNotifier}).
 */
public interface AccountClosedNotifier {

  void accountClosed(UUID accountId);
}
