package com.chm.myfinances.application.investmentproduct;

import com.chm.myfinances.application.investmentcategory.InvestmentCategoryNotFoundException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.application.investmentsubcategory.InvestmentSubcategoryNotFoundException;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link InvestmentProduct}: create/edit/delete and the reads (F008 spec,
 * restructured to pure taxonomy by F022/ADR 0020). New ids come from the {@link IdGenerator} port
 * (ADR 0005).
 *
 * <p>Create and edit (PATCH is a full replace, so it can reclassify a product) verify, in order:
 * the category exists (404); the sub-category, when given, exists (404) and belongs to that
 * category (409, {@link InvestmentSubcategoryMismatchException}); and the name is globally unique
 * (409, F022). {@link #create} is a two-write {@code @Transactional} use case (backend CLAUDE.md
 * "Transactions" rule): the product, then its first holding via {@link InvestmentHoldingService} -
 * a failure between the two must not leave a holding-less product or an orphaned holding. There is
 * no {@code close()} here any more; holdings close, not products (F022). {@link #delete} is only
 * allowed while the product has zero holdings (not zero history - even a closed, empty holding
 * still counts), via {@link InvestmentHoldingRepository#existsByProductId}; otherwise the user
 * removes its holdings first.
 */
@Service
public class InvestmentProductService {

  private final InvestmentProductRepository productRepository;
  private final InvestmentCategoryRepository categoryRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentHoldingService holdingService;
  private final IdGenerator idGenerator;

  public InvestmentProductService(
      InvestmentProductRepository productRepository,
      InvestmentCategoryRepository categoryRepository,
      InvestmentSubcategoryRepository subcategoryRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentHoldingService holdingService,
      IdGenerator idGenerator) {
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.holdingRepository = holdingRepository;
    this.holdingService = holdingService;
    this.idGenerator = idGenerator;
  }

  /**
   * Creates the product and its first holding in {@code accountId} together. {@code
   * InvestmentHoldingService.create} runs its own account/product validation (404/409); if it
   * fails, the whole transaction - including the product insert - rolls back.
   */
  @Transactional
  public InvestmentProduct create(
      UUID accountId,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    requireValidCategoryReferences(investmentCategoryId, investmentSubcategoryId);
    if (productRepository.existsByName(name)) {
      throw new InvestmentProductNameAlreadyExistsException(name);
    }
    InvestmentProduct product =
        productRepository.save(
            InvestmentProduct.create(
                idGenerator.newId(),
                investmentCategoryId,
                investmentSubcategoryId,
                name,
                additionalNotes));
    holdingService.create(product.getId(), accountId, null);
    return product;
  }

  public InvestmentProduct findById(UUID id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new InvestmentProductNotFoundException(id));
  }

  public List<InvestmentProduct> findAll() {
    return productRepository.findAll();
  }

  /**
   * Filtered, paginated global product list (F023 spec's {@code GET /api/investment-products} query
   * params). Every filter dimension is applied in memory - a local, single-user app has few enough
   * products that this needs no DB-level query, the same "computed on read" style as {@link
   * com.chm.myfinances.application.investmentreport.InvestmentAllocationQuery} - and {@code
   * accountId}/{@code status} both read through {@link #holdingRepository}, since neither is a
   * column on {@link InvestmentProduct} any more (F022/ADR 0020). Sorted by name
   * (case-insensitive); the caller's {@link Pageable} only drives paging, not sorting.
   */
  public Page<InvestmentProduct> findAll(InvestmentProductFilter filter, Pageable pageable) {
    List<InvestmentProduct> filtered =
        productRepository.findAll().stream()
            .filter(
                p ->
                    filter.categoryId() == null
                        || p.getInvestmentCategoryId().equals(filter.categoryId()))
            .filter(
                p ->
                    filter.subcategoryId() == null
                        || filter.subcategoryId().equals(p.getInvestmentSubcategoryId()))
            .filter(
                p ->
                    filter.name() == null
                        || p.getName().toLowerCase().contains(filter.name().toLowerCase()))
            .filter(p -> matchesAccountAndStatus(p, filter))
            .sorted(Comparator.comparing(InvestmentProduct::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    return paginate(filtered, pageable);
  }

  private boolean matchesAccountAndStatus(
      InvestmentProduct product, InvestmentProductFilter filter) {
    List<InvestmentHolding> holdings = holdingRepository.findByProductId(product.getId());
    if (filter.accountId() != null
        && holdings.stream().noneMatch(h -> h.getAccountId().equals(filter.accountId()))) {
      return false;
    }
    return switch (filter.status()) {
      case ALL -> true;
      case OPEN -> anyOpen(holdings);
      case CLOSED -> !anyOpen(holdings);
    };
  }

  /**
   * Whether every one of the product's holdings is closed, or it has none at all - F023's derived,
   * not-stored per-row product status shown by the global product list ({@link
   * InvestmentProductResponse}'s {@code closed} field), the exact complement of {@link
   * InvestmentProductStatus#OPEN}'s "at least one holding is open".
   */
  public boolean isClosed(UUID productId) {
    return !anyOpen(holdingRepository.findByProductId(productId));
  }

  private static boolean anyOpen(List<InvestmentHolding> holdings) {
    return holdings.stream().anyMatch(h -> !h.isClosed());
  }

  private static <T> Page<T> paginate(List<T> content, Pageable pageable) {
    if (pageable.isUnpaged()) {
      return new PageImpl<>(content);
    }
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), content.size());
    List<T> pageContent = start >= content.size() ? List.of() : content.subList(start, end);
    return new PageImpl<>(pageContent, pageable, content.size());
  }

  public InvestmentProduct edit(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    InvestmentProduct product = findById(id);
    requireValidCategoryReferences(investmentCategoryId, investmentSubcategoryId);
    if (productRepository.existsByNameAndIdNot(name, id)) {
      throw new InvestmentProductNameAlreadyExistsException(name);
    }
    product.edit(investmentCategoryId, investmentSubcategoryId, name, additionalNotes);
    return productRepository.save(product);
  }

  /**
   * Hard-deletes a product only while it has zero holdings (F022 spec) - even a closed, empty
   * holding still counts and must be removed first.
   */
  public void delete(UUID id) {
    findById(id);
    if (holdingRepository.existsByProductId(id)) {
      throw new InvestmentProductHasHoldingsException(id);
    }
    productRepository.deleteById(id);
  }

  private void requireValidCategoryReferences(
      UUID investmentCategoryId, UUID investmentSubcategoryId) {
    if (!categoryRepository.existsById(investmentCategoryId)) {
      throw new InvestmentCategoryNotFoundException(investmentCategoryId);
    }
    if (investmentSubcategoryId != null) {
      InvestmentSubcategory subcategory =
          subcategoryRepository
              .findById(investmentSubcategoryId)
              .orElseThrow(
                  () -> new InvestmentSubcategoryNotFoundException(investmentSubcategoryId));
      if (!subcategory.getInvestmentCategoryId().equals(investmentCategoryId)) {
        throw new InvestmentSubcategoryMismatchException(investmentSubcategoryId);
      }
    }
  }
}
