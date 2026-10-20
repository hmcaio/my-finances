package com.chm.myfinances.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A settlement note's product lines (F027 spec, ADR 0024): one or more {@link
 * TradeConfirmationLine}s sharing one cash account and one {@code INVESTMENT} account (both held at
 * the {@link Transfer} level, not here - this is a pure value object over the lines). Replaces the
 * old "one transfer, at most one product" shape (ADR 0012) so a single brokerage note - which can
 * list several products under one aggregate tax figure - doesn't have to be split into one
 * fabricated-tax-split transfer per product.
 *
 * <p>Invariants: at least one line; at most one line per distinct {@code productId} may carry a
 * non-null {@code resultingBalance} (a holding has one snapshot per date, so two lines of the same
 * product each trying to set one would be ambiguous).
 */
public final class TradeConfirmation {

  private final List<TradeConfirmationLine> lines;

  private TradeConfirmation(List<TradeConfirmationLine> lines) {
    this.lines = lines;
  }

  /** Builds a confirmation from its lines, validating the cross-line invariants above. */
  public static TradeConfirmation of(List<TradeConfirmationLine> lines) {
    Objects.requireNonNull(lines, "lines must not be null");
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("a trade confirmation requires at least one line");
    }
    List<TradeConfirmationLine> copy = List.copyOf(lines);
    requireAtMostOneResultingBalancePerProduct(copy);
    return new TradeConfirmation(copy);
  }

  private static void requireAtMostOneResultingBalancePerProduct(
      List<TradeConfirmationLine> lines) {
    Set<UUID> seenWithResultingBalance = new HashSet<>();
    for (TradeConfirmationLine line : lines) {
      if (line.resultingBalance() != null && !seenWithResultingBalance.add(line.productId())) {
        throw new IllegalArgumentException(
            "at most one line per product may carry a resultingBalance: " + line.productId());
      }
    }
  }

  /** Immutable; never empty. */
  public List<TradeConfirmationLine> getLines() {
    return lines;
  }

  /**
   * The settlement's net amount (F027 spec's Decisions, implementing ADR 0024): sums each line's
   * {@code quantity * unitPrice} at full precision, nets BUY against SELL (BUY adds, SELL
   * subtracts), adds {@code taxes} once, then rounds only that final figure (HALF_UP, scale 2) -
   * never per line. Positive means a net cost (cash flows from the cash account into the investment
   * account - a net buy); negative means net proceeds (investment account to cash account - a net
   * sell). A net of exactly zero is a structurally nonsensical confirmation and is rejected.
   */
  public BigDecimal netCost(BigDecimal taxes) {
    Objects.requireNonNull(taxes, "taxes must not be null");
    BigDecimal net = BigDecimal.ZERO;
    for (TradeConfirmationLine line : lines) {
      net = line.side() == TradeSide.BUY ? net.add(line.total()) : net.subtract(line.total());
    }
    net = net.add(taxes);
    BigDecimal rounded = net.setScale(2, RoundingMode.HALF_UP);
    if (rounded.signum() == 0) {
      throw new IllegalArgumentException("a trade confirmation's net settlement must not be zero");
    }
    return rounded;
  }
}
