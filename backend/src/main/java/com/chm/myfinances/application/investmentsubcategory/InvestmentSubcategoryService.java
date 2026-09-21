package com.chm.myfinances.application.investmentsubcategory;

import com.chm.myfinances.application.investmentcategory.InvestmentCategoryNotFoundException;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentSubcategory}: create/rename/delete (F008 spec). New ids come from
 * the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Create verifies the parent category exists (404, via {@link InvestmentCategoryRepository}
 * since the domain packages never import each other). Names are unique per parent, not globally
 * (409, {@link InvestmentSubcategoryNameAlreadyExistsException}). There is no re-parenting use case
 * - it would silently reclassify every product beneath. Delete is guarded (409, {@link
 * InvestmentSubcategoryInUseException}) while any product references the sub-category; renaming is
 * always allowed.
 */
@Service
public class InvestmentSubcategoryService {

  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final InvestmentCategoryRepository categoryRepository;
  private final InvestmentProductRepository productRepository;
  private final IdGenerator idGenerator;

  public InvestmentSubcategoryService(
      InvestmentSubcategoryRepository subcategoryRepository,
      InvestmentCategoryRepository categoryRepository,
      InvestmentProductRepository productRepository,
      IdGenerator idGenerator) {
    this.subcategoryRepository = subcategoryRepository;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.idGenerator = idGenerator;
  }

  public InvestmentSubcategory create(UUID investmentCategoryId, String name) {
    if (!categoryRepository.existsById(investmentCategoryId)) {
      throw new InvestmentCategoryNotFoundException(investmentCategoryId);
    }
    if (subcategoryRepository.existsByInvestmentCategoryIdAndName(investmentCategoryId, name)) {
      throw new InvestmentSubcategoryNameAlreadyExistsException(name);
    }
    return subcategoryRepository.save(
        InvestmentSubcategory.create(idGenerator.newId(), investmentCategoryId, name));
  }

  public InvestmentSubcategory rename(UUID id, String newName) {
    InvestmentSubcategory subcategory =
        subcategoryRepository
            .findById(id)
            .orElseThrow(() -> new InvestmentSubcategoryNotFoundException(id));
    if (subcategoryRepository.existsByInvestmentCategoryIdAndNameAndIdNot(
        subcategory.getInvestmentCategoryId(), newName, id)) {
      throw new InvestmentSubcategoryNameAlreadyExistsException(newName);
    }
    subcategory.rename(newName);
    return subcategoryRepository.save(subcategory);
  }

  public void delete(UUID id) {
    if (subcategoryRepository.findById(id).isEmpty()) {
      throw new InvestmentSubcategoryNotFoundException(id);
    }
    if (productRepository.existsByInvestmentSubcategoryId(id)) {
      throw new InvestmentSubcategoryInUseException(id);
    }
    subcategoryRepository.deleteById(id);
  }
}
