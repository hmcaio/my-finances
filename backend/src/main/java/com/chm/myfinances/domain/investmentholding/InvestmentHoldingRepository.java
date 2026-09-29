package com.chm.myfinances.domain.investmentholding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentHolding} (ADR 0004: domain/application logic sits behind
 * ports, isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentholding}.
 */
public interface InvestmentHoldingRepository {

  InvestmentHolding save(InvestmentHolding holding);

  Optional<InvestmentHolding> findById(UUID id);

  List<InvestmentHolding> findAll();

  /** Every holding of a product, across every account it's held in. */
  List<InvestmentHolding> findByProductId(UUID productId);

  /** Every holding in an account, across every product held there. */
  List<InvestmentHolding> findByAccountId(UUID accountId);

  /** The holding of this product in this account, if one has been explicitly created. */
  Optional<InvestmentHolding> findByProductIdAndAccountId(UUID productId, UUID accountId);

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether this exact (product, account) pair already has a holding - the create guard. */
  boolean existsByProductIdAndAccountId(UUID productId, UUID accountId);

  /**
   * Whether the product has at least one holding, open or closed - the product delete guard (F022:
   * a product can only be hard-deleted with zero holdings, not zero history).
   */
  boolean existsByProductId(UUID productId);

  /** Whether the account owns any holding, open or closed - part of the account delete guard. */
  boolean existsByAccountId(UUID accountId);

  /**
   * Whether the account owns at least one holding that isn't closed - backs {@code
   * AccountService.close}'s rule that an {@code INVESTMENT} account can only be closed once all its
   * holdings are (F008 spec, moved from product to holding by F022).
   */
  boolean existsOpenByAccountId(UUID accountId);
}
