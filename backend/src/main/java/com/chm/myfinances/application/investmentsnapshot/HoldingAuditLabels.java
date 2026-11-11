package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@code entityLabel} of a holding's snapshot in the Activity feed (F040): "Product (Account)".
 * A snapshot's own audit fields carry only the holding id (and an UPDATE's diff only the balance),
 * so without a label the Activity row of a snapshot would not say which holding it belongs to.
 * Resolved at write time like every other audit label (ADR 0022), so a later rename never changes
 * an old row. Not an {@code AuditReferenceLabels} field: that resolves one id to one name, and a
 * holding has no single-field label.
 */
@Component
public class HoldingAuditLabels {

  /** {@code entity_label} is {@code varchar(200)}; two 100-character names can overflow it. */
  static final int MAX_LABEL_LENGTH = 200;

  private final InvestmentProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final boolean enabled;

  @Autowired
  public HoldingAuditLabels(
      InvestmentProductRepository productRepository, AccountRepository accountRepository) {
    this(productRepository, accountRepository, true);
  }

  private HoldingAuditLabels(
      InvestmentProductRepository productRepository,
      AccountRepository accountRepository,
      boolean enabled) {
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.enabled = enabled;
  }

  /** No label: for tests that build a service without caring about the Activity label. */
  public static HoldingAuditLabels none() {
    return new HoldingAuditLabels(null, null, false);
  }

  /** "Product (Account)", or {@code null} when either name no longer resolves. */
  public String labelFor(InvestmentHolding holding) {
    if (!enabled) {
      return null;
    }
    Optional<String> product =
        productRepository.findById(holding.getProductId()).map(p -> p.getName());
    Optional<String> account =
        accountRepository.findById(holding.getAccountId()).map(a -> a.getName());
    if (product.isEmpty() || account.isEmpty()) {
      return null;
    }
    String label = product.get() + " (" + account.get() + ")";
    return label.length() <= MAX_LABEL_LENGTH ? label : label.substring(0, MAX_LABEL_LENGTH);
  }
}
