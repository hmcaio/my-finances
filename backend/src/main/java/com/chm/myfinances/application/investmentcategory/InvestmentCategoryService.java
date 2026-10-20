package com.chm.myfinances.application.investmentcategory;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read use case for {@link InvestmentCategory}: the nested category+sub-category list (F008 spec).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were deleted — every row is now
 * fixed, migration-seeded content (F013's seeded set, unchanged). This leaves only the read side,
 * which every other investment feature (F009, F022, F026, F027) already depends on being stable.
 */
@Service
public class InvestmentCategoryService {

  private static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER;

  private final InvestmentCategoryRepository categoryRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;

  public InvestmentCategoryService(
      InvestmentCategoryRepository categoryRepository,
      InvestmentSubcategoryRepository subcategoryRepository) {
    this.categoryRepository = categoryRepository;
    this.subcategoryRepository = subcategoryRepository;
  }

  /** Every category with its sub-categories nested, both levels sorted by name. */
  public List<InvestmentCategoryWithSubcategories> findAllWithSubcategories() {
    List<InvestmentSubcategory> subcategories = subcategoryRepository.findAll();
    return categoryRepository.findAll().stream()
        .sorted(Comparator.comparing(InvestmentCategory::getName, BY_NAME))
        .map(category -> nest(category, subcategories))
        .toList();
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
}
