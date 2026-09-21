package com.chm.myfinances.domain.investmentproduct;

import java.util.UUID;

/**
 * Repository port for {@code InvestmentProduct} (ADR 0004: domain/application logic sits behind
 * ports, isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentproduct}.
 */
public interface InvestmentProductRepository {

  /**
   * Whether the account owns at least one product that isn't closed - backs {@code
   * AccountService.close}'s rule that an {@code INVESTMENT} account can only be closed once all its
   * products are (F008 spec).
   */
  boolean existsOpenByAccountId(UUID accountId);
}
