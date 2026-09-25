package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.account.AccountService;
import com.chm.myfinances.domain.account.Account;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code Account} (F003 spec). {@code DELETE} works only for an account with no
 * history (ADR 0017); otherwise it is closed.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

  private final AccountService accountService;
  private final AccountBalanceQuery accountBalanceQuery;
  private final Clock clock;

  public AccountController(
      AccountService accountService, AccountBalanceQuery accountBalanceQuery, Clock clock) {
    this.accountService = accountService;
    this.accountBalanceQuery = accountBalanceQuery;
    this.clock = clock;
  }

  @GetMapping
  public List<AccountResponse> list(
      @RequestParam(name = "includeClosed", defaultValue = "false") boolean includeClosed) {
    LocalDate now = LocalDate.now(clock);
    return accountService.findAll(includeClosed).stream()
        .map(account -> toResponse(account, now))
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
    Account account =
        accountService.create(
            request.name(),
            request.institutionId(),
            request.type(),
            request.openingBalance(),
            request.openingBalanceDate());
    return toResponse(account, LocalDate.now(clock));
  }

  @GetMapping("/{id}")
  public AccountResponse get(
      @PathVariable UUID id,
      @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    Account account = accountService.findById(id);
    return toResponse(account, asOf != null ? asOf : LocalDate.now(clock));
  }

  @PatchMapping("/{id}")
  public AccountResponse edit(
      @PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request) {
    Account account = accountService.edit(id, request.name(), request.institutionId());
    return toResponse(account, LocalDate.now(clock));
  }

  @PostMapping("/{id}/close")
  public AccountResponse close(@PathVariable UUID id) {
    Account account = accountService.close(id);
    return toResponse(account, LocalDate.now(clock));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    accountService.delete(id);
  }

  private AccountResponse toResponse(Account account, LocalDate asOf) {
    return AccountResponse.from(account, accountBalanceQuery.balanceAsOf(account, asOf));
  }
}
