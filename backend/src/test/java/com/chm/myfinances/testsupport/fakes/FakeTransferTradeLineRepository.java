package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory test double for {@link TransferTradeLineRepository} (F027 spec, ADR 0024), shared
 * across application-service tests. {@link #add} is a test-only seeding method (the real port has
 * no write side - lines are written as part of saving a {@code Transfer} aggregate, out of this
 * fake's scope).
 */
public final class FakeTransferTradeLineRepository implements TransferTradeLineRepository {

  private final List<TransferTradeLine> lines = new ArrayList<>();

  public void add(TransferTradeLine line) {
    lines.add(line);
  }

  @Override
  public List<TransferTradeLine> findByProductId(UUID productId) {
    return lines.stream().filter(l -> l.productId().equals(productId)).toList();
  }

  @Override
  public List<TransferTradeLine> findAll() {
    return List.copyOf(lines);
  }

  @Override
  public boolean existsByProductIdAndAccountId(UUID productId, UUID accountId) {
    return lines.stream()
        .anyMatch(
            l ->
                l.productId().equals(productId)
                    && (l.fromAccountId().equals(accountId) || l.toAccountId().equals(accountId)));
  }
}
