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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentProduct} (F008 spec). A plain list (an account holds a handful of
 * products), optionally filtered by {@code ?accountId=}.
 */
@RestController
@RequestMapping("/api/investment-products")
public class InvestmentProductController {

  private final InvestmentProductService productService;

  public InvestmentProductController(InvestmentProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  public List<InvestmentProductResponse> list(
      @RequestParam(name = "accountId", required = false) UUID accountId) {
    return productService.findAll(accountId).stream().map(this::toResponse).toList();
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
            request.name()));
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
            request.accountId(),
            request.investmentCategoryId(),
            request.investmentSubcategoryId(),
            request.name()));
  }

  @PostMapping("/{id}/close")
  public InvestmentProductResponse close(@PathVariable UUID id) {
    return toResponse(productService.close(id));
  }

  /** {@code 409} when the product has history (F009): the user closes it instead. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    productService.delete(id);
  }

  private InvestmentProductResponse toResponse(InvestmentProduct product) {
    return InvestmentProductResponse.from(product, productService.hasHistory(product.getId()));
  }
}
