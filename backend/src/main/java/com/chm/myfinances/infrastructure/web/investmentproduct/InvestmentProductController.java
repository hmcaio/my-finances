package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.application.investmentproduct.InvestmentProductFilter;
import com.chm.myfinances.application.investmentproduct.InvestmentProductService;
import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentProduct} (F008 spec, restructured to pure taxonomy by F022/ADR
 * 0020). Close/snapshot actions moved to {@code InvestmentHoldingController}.
 */
@RestController
@RequestMapping("/api/investment-products")
public class InvestmentProductController {

  private final InvestmentProductService productService;

  public InvestmentProductController(InvestmentProductService productService) {
    this.productService = productService;
  }

  /**
   * Filtered, paginated list - {@code GET /api/investment-products?categoryId=&subcategoryId=&
   * accountId=&name=&status=&page=&size=}. Defaults to 20 per page, name ascending. {@code status}
   * defaults to {@code OPEN} (F023 spec's "Decisions": derived from the product's holdings, not
   * stored). Returns {@link PagedModel}, matching {@code TransactionController}'s list shape
   * (backend {@code CLAUDE.md}'s "List endpoints" convention) - a breaking shape change from F022's
   * plain list, fine pre-1.0/local app.
   */
  @GetMapping
  public PagedModel<InvestmentProductResponse> list(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID subcategoryId,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false, defaultValue = "OPEN") InvestmentProductStatus status,
      @PageableDefault(size = 20) Pageable pageable) {
    InvestmentProductFilter filter =
        new InvestmentProductFilter(categoryId, subcategoryId, accountId, name, status);
    Page<InvestmentProduct> page = productService.findAll(filter, pageable);
    return new PagedModel<>(page.map(this::toResponse));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InvestmentProductResponse create(
      @Valid @RequestBody CreateInvestmentProductRequest request) {
    return toResponse(
        productService.create(
            request.accountId(),
            request.investmentCategoryId(),
            request.investmentSubcategoryId(),
            request.name(),
            request.additionalNotes(),
            request.ticker(),
            request.segmentId()));
  }

  @GetMapping("/{id}")
  public InvestmentProductResponse get(@PathVariable UUID id) {
    return toResponse(productService.findById(id));
  }

  @PatchMapping("/{id}")
  public InvestmentProductResponse edit(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvestmentProductRequest request) {
    return toResponse(
        productService.edit(
            id,
            request.investmentCategoryId(),
            request.investmentSubcategoryId(),
            request.name(),
            request.additionalNotes(),
            request.ticker(),
            request.segmentId()));
  }

  /**
   * {@code 409} while the product still has at least one holding, even a closed and empty one
   * (F022): the user removes its holdings first.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    productService.delete(id);
  }

  private InvestmentProductResponse toResponse(InvestmentProduct product) {
    return InvestmentProductResponse.from(product, productService.isClosed(product.getId()));
  }
}
