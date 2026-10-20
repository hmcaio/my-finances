package com.chm.myfinances.application.transfer;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Line-granularity read of one product's trade history (F027 spec, ADR 0024): {@code GET
 * /api/trade-confirmation-lines?productId=}, replacing {@code InvestmentProductDetailPage}'s old
 * use of {@code GET /api/transfers?investmentProductId=} for its trade table now that a
 * confirmation's lines, not the confirmation itself, are the unit a per-product table displays
 * (Taxes dropped - a confirmation's taxes cover its whole settlement, not one product). Each row
 * carries its parent {@code transferId} so the frontend can link back to the full confirmation.
 */
@Service
public class TradeConfirmationLineQuery {

  private final TransferTradeLineRepository tradeLineRepository;
  private final InvestmentProductRepository productRepository;

  public TradeConfirmationLineQuery(
      TransferTradeLineRepository tradeLineRepository,
      InvestmentProductRepository productRepository) {
    this.tradeLineRepository = tradeLineRepository;
    this.productRepository = productRepository;
  }

  /** Every line of {@code productId}'s trades, most recent first. 404 for an unknown product. */
  public List<TransferTradeLine> findByProduct(UUID productId) {
    if (!productRepository.existsById(productId)) {
      throw new InvestmentProductNotFoundException(productId);
    }
    return tradeLineRepository.findByProductId(productId).stream()
        .sorted(Comparator.comparing(TransferTradeLine::date).reversed())
        .toList();
  }
}
