package com.chm.myfinances.application.transfer;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code Transfer} is created/edited with the same account on both sides (F005 spec's
 * invariant: "{@code fromAccountId != toAccountId}"). Maps to 400 - unlike the closed-account case
 * (a state conflict, 409), this is a malformed request: no combination of server state ever makes
 * {@code fromAccountId == toAccountId} valid, so it's a client input error rather than a conflict
 * with existing data.
 *
 * <p>{@code Transfer}'s own constructor/{@code edit} (F005 spec) enforce the same invariant at the
 * domain level as defense in depth; this exception is how the application layer translates that
 * into a proper HTTP status before ever reaching the domain, mirroring how {@code
 * AccountClosedException} wraps {@code Account.requireOpen()}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SameAccountTransferException extends RuntimeException {

  public SameAccountTransferException(UUID accountId) {
    super("fromAccountId and toAccountId must not be the same: " + accountId);
  }
}
