package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.application.investmentproduct.InvestmentProductService;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentProduct} (F008 spec, restructured to pure taxonomy by F022/ADR
 * 0020). A plain list of every product - {@code ?accountId=} is gone, no longer meaningful on the
 * product (F023 adds real filtering). Close/snapshot actions moved to {@code
 * InvestmentHoldingController}.
 */
@RestController
@RequestMapping("/api/investment-products")
public class InvestmentProductController {

  private final InvestmentProductService productService;

  public InvestmentProductController(InvestmentProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  public List<InvestmentProductResponse> list() {
    return productService.findAll().stream().map(InvestmentProductResponse::from).toList();
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
            request.additionalNotes()));
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
            request.additionalNotes()));
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
    return InvestmentProductResponse.from(product);
  }
}
