package com.chm.myfinances.domain.investmentholding;

import java.util.UUID;

/**
 * Port answering "does this holding have any history?" (F022 spec): a holding can only be
 * hard-deleted while it has none (a snapshot row, or a transfer tagged with its product *and* its
 * account), otherwise it can only be closed. Same shape as the old {@code
 * HasInvestmentHistoryChecker} (F008/F009) this supersedes: the owner declares the port, the
 * feature that has the data ({@code infrastructure/investmentholding/RealHasHoldingHistoryChecker})
 * implements it.
 */
public interface HasHoldingHistoryChecker {

  boolean hasHistory(UUID investmentHoldingId);
}
