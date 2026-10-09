package com.chm.myfinances.application.auditlog;

import static com.chm.myfinances.testsupport.AuditSnapshotCompleteness.declaredFieldNames;
import static com.chm.myfinances.testsupport.AuditSnapshotCompleteness.technicalFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.infrastructure.persistence.account.AccountJpaEntity;
import com.chm.myfinances.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.chm.myfinances.infrastructure.persistence.transfer.TransferJpaEntity;
import com.chm.myfinances.infrastructure.persistence.transfer.TransferTradeLineJpaEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Guards that {@code toAuditSnapshot()} covers every persisted field, per aggregate, with an
 * explicit exclude list (F025 spec, ADR 0022: "a completeness test with an explicit exclude list
 * catches silent gaps" - a field added later, like F024's fuel columns on {@code Transaction},
 * can't be silently unaudited). PR1 covers the three aggregates it instruments; PR2 extends this to
 * every other aggregate.
 */
class AuditSnapshotCompletenessTest {

  @Test
  void transactionSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(TransactionJpaEntity.class);
    persisted.removeAll(technicalFields());

    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            BigDecimal.TEN,
            UUID.randomUUID(),
            CategoryType.EXPENSE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "desc",
            null);

    assertThat(transaction.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void accountSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(AccountJpaEntity.class);
    persisted.removeAll(technicalFields());

    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            UUID.randomUUID(),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.of(2026, 1, 1));

    assertThat(account.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  /**
   * {@code Transfer}'s own columns, excluding the trade confirmation's lines (a separate child
   * table, checked below) - every other persisted field on {@code transfers} must be a top-level
   * snapshot key.
   */
  @Test
  void transferSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(TransferJpaEntity.class);
    persisted.removeAll(technicalFields());

    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.TEN,
            "desc",
            null);

    assertThat(transfer.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  /**
   * {@code transfer_trade_lines}'s own columns, excluding {@code id} (technical) and {@code
   * transferId} (the FK back to the parent, already identified by the entry itself) - every other
   * field must appear in each line's flattened map under {@code tradeConfirmationLines}.
   */
  @Test
  void transferTradeLineSnapshotCoversEveryPersistedFieldExceptTechnicalAndParentFk() {
    Set<String> persisted = declaredFieldNames(TransferTradeLineJpaEntity.class);
    persisted.removeAll(technicalFields());
    persisted.remove("transferId");

    UUID productId = UUID.randomUUID();
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                new TradeConfirmationLine(
                    productId, TradeSide.BUY, BigDecimal.TEN, BigDecimal.ONE, null, false)));
    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "desc",
            null,
            BigDecimal.ONE,
            confirmation);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines =
        (List<Map<String, Object>>) transfer.toAuditSnapshot().get("tradeConfirmationLines");

    assertThat(lines).isNotEmpty();
    assertThat(lines.get(0).keySet()).containsAll(persisted);
  }
}
