package com.chm.myfinances.infrastructure.account;

import com.chm.myfinances.domain.account.AccountClosedNotifier;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only {@link AccountClosedNotifier} implementation until F007 (Recurring Templates) exists.
 * F007 will replace this with a real listener that deactivates any {@code RecurringTemplate} still
 * pointing at the closed account (PRD S5.4). Kept as a bean so {@link
 * com.chm.myfinances.application.account.AccountService} can depend on the port today without a
 * missing-bean wiring error.
 */
@Component
public class NoOpAccountClosedNotifier implements AccountClosedNotifier {

  @Override
  public void accountClosed(UUID accountId) {
    // Intentionally no-op - see class javadoc.
  }
}
