package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.application.investmentproduct.InvestmentProductFilter;
import com.chm.myfinances.application.investmentproduct.InvestmentProductService;
import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-null, not-blank, size",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-category-not-found, "
              + "urn:my-finances:error:investment-subcategory-not-found, "
              + "urn:my-finances:error:investment-segment-not-found, "
              + "urn:my-finances:error:account-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:investment-subcategory-mismatch, "
              + "urn:my-finances:error:investment-product-name-already-exists, "
              + "urn:my-finances:error:investment-account-required",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-product-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentProductResponse get(@PathVariable UUID id) {
    return toResponse(productService.findById(id));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-null, not-blank, size",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-product-not-found, "
              + "urn:my-finances:error:investment-category-not-found, "
              + "urn:my-finances:error:investment-subcategory-not-found, "
              + "urn:my-finances:error:investment-segment-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:investment-subcategory-mismatch, "
              + "urn:my-finances:error:investment-product-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-product-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-product-has-holdings",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    productService.delete(id);
  }

  private InvestmentProductResponse toResponse(InvestmentProduct product) {
    return InvestmentProductResponse.from(product, productService.isClosed(product.getId()));
  }
}
