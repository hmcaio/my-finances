package com.chm.myfinances.application.investmentcategory;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentCategory}: create/rename/delete and the nested read (F008 spec).
 * New ids come from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Create/rename reject a duplicate name (409, {@link
 * InvestmentCategoryNameAlreadyExistsException}, global scope). Delete is guarded (409, {@link
 * InvestmentCategoryInUseException}) while the category still has sub-categories or any product is
 * classified under it; renaming is always allowed.
 */
@Service
public class InvestmentCategoryService {

  private static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER;

  private final InvestmentCategoryRepository categoryRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final InvestmentProductRepository productRepository;
  private final IdGenerator idGenerator;

  public InvestmentCategoryService(
      InvestmentCategoryRepository categoryRepository,
      InvestmentSubcategoryRepository subcategoryRepository,
      InvestmentProductRepository productRepository,
      IdGenerator idGenerator) {
    this.categoryRepository = categoryRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.productRepository = productRepository;
    this.idGenerator = idGenerator;
  }

  public InvestmentCategory create(String name) {
    if (categoryRepository.existsByName(name)) {
      throw new InvestmentCategoryNameAlreadyExistsException(name);
    }
    return categoryRepository.save(InvestmentCategory.create(idGenerator.newId(), name));
  }

  /** Every category with its sub-categories nested, both levels sorted by name. */
  public List<InvestmentCategoryWithSubcategories> findAllWithSubcategories() {
    List<InvestmentSubcategory> subcategories = subcategoryRepository.findAll();
    return categoryRepository.findAll().stream()
        .sorted(Comparator.comparing(InvestmentCategory::getName, BY_NAME))
        .map(category -> nest(category, subcategories))
        .toList();
  }

  /** One category with its sub-categories nested (404 when unknown). */
  public InvestmentCategoryWithSubcategories findWithSubcategories(UUID id) {
    InvestmentCategory category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new InvestmentCategoryNotFoundException(id));
    return nest(category, subcategoryRepository.findAll());
  }

  private static InvestmentCategoryWithSubcategories nest(
      InvestmentCategory category, List<InvestmentSubcategory> allSubcategories) {
    return new InvestmentCategoryWithSubcategories(
        category,
        allSubcategories.stream()
            .filter(s -> s.getInvestmentCategoryId().equals(category.getId()))
            .sorted(Comparator.comparing(InvestmentSubcategory::getName, BY_NAME))
            .toList());
  }

  public InvestmentCategory rename(UUID id, String newName) {
    InvestmentCategory category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new InvestmentCategoryNotFoundException(id));
    if (categoryRepository.existsByNameAndIdNot(newName, id)) {
      throw new InvestmentCategoryNameAlreadyExistsException(newName);
    }
    category.rename(newName);
    return categoryRepository.save(category);
  }

  public void delete(UUID id) {
    if (!categoryRepository.existsById(id)) {
      throw new InvestmentCategoryNotFoundException(id);
    }
    if (subcategoryRepository.existsByInvestmentCategoryId(id)
        || productRepository.existsByInvestmentCategoryId(id)) {
      throw new InvestmentCategoryInUseException(id);
    }
    categoryRepository.deleteById(id);
  }
}
