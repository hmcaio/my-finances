package com.chm.myfinances.infrastructure.web.ledger;

import com.chm.myfinances.domain.transfer.TradeSide;
import java.util.UUID;

/**
 * The part of a trade-confirmation line a Ledger row needs (F037): enough for the Buy/Sell chips.
 * The full line (quantity, price, resulting balance) is fetched with the transfer when it is
 * edited.
 */
public record LedgerTradeLineResponse(TradeSide side, UUID productId) {}
