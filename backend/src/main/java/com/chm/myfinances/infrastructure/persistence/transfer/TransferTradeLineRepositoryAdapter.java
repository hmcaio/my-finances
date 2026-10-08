package com.chm.myfinances.infrastructure.persistence.transfer;

import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link TransferTradeLineRepository} port (F027 spec, ADR 0024):
 * reads {@code transfer_trade_lines} directly, denormalizing each line with its parent {@code
 * transfers} row's date/accounts in memory (a local, single-user app never has enough trade rows
 * for the extra round trip to matter, backend {@code CLAUDE.md}'s "filter in memory" convention)
 * rather than a joined projection query.
 */
@Component
public class TransferTradeLineRepositoryAdapter implements TransferTradeLineRepository {

  private final TransferTradeLineJpaRepository lineJpaRepository;
  private final TransferJpaRepository transferJpaRepository;

  public TransferTradeLineRepositoryAdapter(
      TransferTradeLineJpaRepository lineJpaRepository,
      TransferJpaRepository transferJpaRepository) {
    this.lineJpaRepository = lineJpaRepository;
    this.transferJpaRepository = transferJpaRepository;
  }

  @Override
  public List<TransferTradeLine> findByProductId(UUID productId) {
    return toDomain(lineJpaRepository.findByProductId(productId));
  }

  @Override
  public List<TransferTradeLine> findAll() {
    return toDomain(lineJpaRepository.findAll());
  }

  @Override
  public boolean existsByProductIdAndAccountId(UUID productId, UUID accountId) {
    return findByProductId(productId).stream()
        .anyMatch(l -> l.fromAccountId().equals(accountId) || l.toAccountId().equals(accountId));
  }

  private List<TransferTradeLine> toDomain(List<TransferTradeLineJpaEntity> lines) {
    if (lines.isEmpty()) {
      return List.of();
    }
    List<UUID> transferIds =
        lines.stream().map(TransferTradeLineJpaEntity::getTransferId).distinct().toList();
    Map<UUID, TransferJpaEntity> transfersById = new HashMap<>();
    transferJpaRepository.findAllById(transferIds).forEach(t -> transfersById.put(t.getId(), t));
    return lines.stream()
        .map(
            l -> {
              TransferJpaEntity transfer = transfersById.get(l.getTransferId());
              return new TransferTradeLine(
                  l.getTransferId(),
                  transfer.getDate(),
                  transfer.getFromAccountId(),
                  transfer.getToAccountId(),
                  l.getProductId(),
                  l.getSide(),
                  l.getQuantity(),
                  l.getUnitPrice(),
                  l.getResultingBalance());
            })
        .toList();
  }
}
