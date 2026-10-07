package com.chm.myfinances.application.allocationplan;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.allocationplan.AllocationPlan;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanRepository;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersionRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link AllocationPlan}/{@link AllocationPlanVersion}: resolve the effective
 * version, list history, and set the allocation (F026 spec, ADR 0023). New ids come from the
 * {@link IdGenerator} port (ADR 0005).
 *
 * <p>The {@link AllocationPlan} marker row isn't user-creatable - {@link #setAllocation} creates
 * it on first use (mirroring {@code BudgetService.create}'s two-write shape, but here there's
 * only ever one plan row, created implicitly rather than per category). {@link #getCurrent}/{@link
 * #findVersions} never create it - a read before any allocation has ever been set simply reports
 * nothing (an empty {@link Optional}/list), not a side-effecting row.
 *
 * <p>{@link #setAllocation} validates every entry's product exists (404, {@link
 * InvestmentProductNotFoundException}) and is classified under the "REITs (FIIs)" sub-category
 * (409, {@link AllocationPlanEntryNotFiiException}), then - before touching the domain, which
 * would otherwise raise a plain, unmapped {@code IllegalArgumentException} (500) - rejects a
 * duplicate product (400, {@link AllocationPlanDuplicateProductException}) and a sum other than
 * exactly 100 (400, {@link AllocationPlanSumInvalidException}). Mirrors {@code
 * BudgetService.setCap}'s same-month "replace, don't duplicate" rule.
 */
@Service
public class AllocationPlanService {

  /** The sub-category name every allocation-plan entry's product must be classified under. */
  public static final String FII_SUBCATEGORY_NAME = "REITs (FIIs)";

  private static final Logger log = LoggerFactory.getLogger(AllocationPlanService.class);
  private static final BigDecimal FULL_ALLOCATION = new BigDecimal("100");

  private final AllocationPlanRepository planRepository;
  private final AllocationPlanVersionRepository versionRepository;
  private final InvestmentProductRepository productRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final IdGenerator idGenerator;

  public AllocationPlanService(
      AllocationPlanRepository planRepository,
      AllocationPlanVersionRepository versionRepository,
      InvestmentProductRepository productRepository,
      InvestmentSubcategoryRepository subcategoryRepository,
      IdGenerator idGenerator) {
    this.planRepository = planRepository;
    this.versionRepository = versionRepository;
    this.productRepository = productRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.idGenerator = idGenerator;
  }

  /** The version effective for {@code month}, or empty if no allocation has ever been set. */
  public Optional<AllocationPlanVersion> getCurrent(YearMonth month) {
    return planRepository
        .findFirst()
        .flatMap(
            plan ->
                AllocationPlanVersion.resolveEffective(
                    versionRepository.findByPlanId(plan.getId()), month));
  }

  /** Every version ever set, in no particular order, or empty if none has ever been set. */
  public List<AllocationPlanVersion> findVersions() {
    return planRepository.findFirst().map(p -> versionRepository.findByPlanId(p.getId())).orElse(List.of());
  }

  /**
   * Creates a new version effective from {@code effectiveFrom}, or replaces the existing version
   * for that exact month if one already exists (the one allowed same-month correction).
   */
  @Transactional
  public AllocationPlanVersion setAllocation(List<AllocationPlanEntry> entries, YearMonth effectiveFrom) {
    requireValidEntries(entries);
    AllocationPlan plan =
        planRepository
            .findFirst()
            .orElseGet(() -> planRepository.save(AllocationPlan.create(idGenerator.newId())));

    Optional<AllocationPlanVersion> existing =
        versionRepository.findByPlanIdAndEffectiveFrom(plan.getId(), effectiveFrom);
    if (existing.isPresent()) {
      AllocationPlanVersion version = existing.get();
      version.updateEntries(entries);
      AllocationPlanVersion replaced = versionRepository.save(version);
      log.info("Allocation plan {}: version effective {} replaced", plan.getId(), effectiveFrom);
      return replaced;
    }
    AllocationPlanVersion version =
        AllocationPlanVersion.create(idGenerator.newId(), plan.getId(), entries, effectiveFrom);
    AllocationPlanVersion saved = versionRepository.save(version);
    log.info("Allocation plan {}: new version effective {}", plan.getId(), effectiveFrom);
    return saved;
  }

  private void requireValidEntries(List<AllocationPlanEntry> entries) {
    Set<UUID> seenProducts = new HashSet<>();
    BigDecimal sum = BigDecimal.ZERO;
    for (AllocationPlanEntry entry : entries) {
      InvestmentProduct product = requireFiiProduct(entry.investmentProductId());
      if (!seenProducts.add(product.getId())) {
        throw new AllocationPlanDuplicateProductException(product.getId());
      }
      sum = sum.add(entry.targetPercentage());
    }
    if (sum.compareTo(FULL_ALLOCATION) != 0) {
      throw new AllocationPlanSumInvalidException(sum);
    }
  }

  private InvestmentProduct requireFiiProduct(UUID productId) {
    InvestmentProduct product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new InvestmentProductNotFoundException(productId));
    UUID subcategoryId = product.getInvestmentSubcategoryId();
    boolean isFii =
        subcategoryId != null
            && subcategoryRepository
                .findById(subcategoryId)
                .map(InvestmentSubcategory::getName)
                .map(FII_SUBCATEGORY_NAME::equals)
                .orElse(false);
    if (!isFii) {
      throw new AllocationPlanEntryNotFiiException(productId);
    }
    return product;
  }
}
