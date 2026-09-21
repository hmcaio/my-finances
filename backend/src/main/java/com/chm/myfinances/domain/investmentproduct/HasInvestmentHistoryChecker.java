package com.chm.myfinances.domain.investmentproduct;

import java.util.UUID;

/**
 * Port answering "does this product have any history?" (PRD S5.8's delete-safety rule): a product
 * can only be hard-deleted while it has none, otherwise it can only be closed. History means
 * snapshots or buy/sell transfers, both F009's tables - so this feature defines the port and ships
 * an implementation that always answers {@code false}, and F009 replaces it with the real one
 * without F008 depending on tables that don't exist yet. Same shape as {@code
 * AccountClosedNotifier}: the owner declares the port, the feature that has the data implements it.
 */
public interface HasInvestmentHistoryChecker {

  boolean hasHistory(UUID investmentProductId);
}
