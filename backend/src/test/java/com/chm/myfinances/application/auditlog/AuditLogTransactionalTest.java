package com.chm.myfinances.application.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.chm.myfinances.application.account.AccountService;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.testsupport.AbstractTransactionalBoundaryTest;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) of ADR 0022's "same
 * transaction" decision from the other direction: when the audit insert itself fails, the business
 * write it was meant to accompany must roll back too, not just commit with no trace of the change.
 * {@code AccountService.create} is a convenient, simple instrumented use case to exercise this
 * through; the same {@code @Transactional} mechanics apply to every other instrumented service.
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} (see {@code
 * TransferServiceTransactionalTest}'s javadoc for why).
 */
class AuditLogTransactionalTest extends AbstractTransactionalBoundaryTest {

  @Autowired private AccountService accountService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;

  @Test
  void createRollsBackTheAccountWhenTheAuditInsertFails() {
    willThrow(new RuntimeException("simulated failure writing the audit entry"))
        .given(auditLog)
        .record(any(AuditEntry.class));

    assertThatThrownBy(
            () ->
                accountService.create(
                    "Audit Rollback Test Account",
                    TestInstitutions.builtInId(institutionRepository),
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(RuntimeException.class);

    // The account, written first, must have rolled back with the failed audit insert.
    boolean exists =
        accountRepository.findAll().stream()
            .anyMatch(a -> a.getName().equals("Audit Rollback Test Account"));
    assertThat(exists).isFalse();
  }
}
