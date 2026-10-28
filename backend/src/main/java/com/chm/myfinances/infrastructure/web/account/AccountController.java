package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.account.AccountService;
import com.chm.myfinances.domain.account.Account;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
    List<Account> accounts = accountService.findAll(includeClosed);
    Map<UUID, BigDecimal> balances =
        accountBalanceQuery.balancesAsOf(accounts, LocalDate.now(clock));
    return accounts.stream()
        .map(account -> AccountResponse.from(account, balances.get(account.getId())))
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size, not-null, "
              + "opening-fields-consistent-with-type",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:institution-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:account-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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

  @GetMapping("/count")
  public AccountCountResponse count() {
    return new AccountCountResponse(accountService.count());
  }

  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:account-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AccountResponse get(
      @PathVariable UUID id,
      @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    Account account = accountService.findById(id);
    return toResponse(account, asOf != null ? asOf : LocalDate.now(clock));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size, not-null",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:account-not-found, "
              + "urn:my-finances:error:institution-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:account-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AccountResponse edit(
      @PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request) {
    Account account = accountService.edit(id, request.name(), request.institutionId());
    return toResponse(account, LocalDate.now(clock));
  }

  @PostMapping("/{id}/close")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:account-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:account-already-closed, "
              + "urn:my-finances:error:investment-account-has-open-holdings",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AccountResponse close(@PathVariable UUID id) {
    Account account = accountService.close(id);
    return toResponse(account, LocalDate.now(clock));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:account-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:account-has-history",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    accountService.delete(id);
  }

  private AccountResponse toResponse(Account account, LocalDate asOf) {
    return AccountResponse.from(account, accountBalanceQuery.balanceAsOf(account, asOf));
  }
}
