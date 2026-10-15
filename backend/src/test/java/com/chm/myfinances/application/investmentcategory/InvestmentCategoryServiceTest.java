package com.chm.myfinances.application.investmentcategory;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentCategoryService}, against hand-written fakes - plain
 * JUnit, no Spring context (F008 spec).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were deleted - every row is now
 * fixed, migration-seeded content, so only the nested read is left to cover. Fixtures are built
 * directly through {@code reconstitute} (the repository-fixture shape every other feature's tests
 * already use for these now-locked taxonomies), not through the service.
 */
class InvestmentCategoryServiceTest {

  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final InvestmentCategoryService service =
      new InvestmentCategoryService(categoryRepository, subcategoryRepository);

  private InvestmentCategory category(String name) {
    return categoryRepository.save(InvestmentCategory.reconstitute(UUID.randomUUID(), name));
  }

  @Test
  void findAllWithSubcategoriesNestsTheSubcategoriesAndSortsByName() {
    InvestmentCategory variable = category("Variable Income");
    InvestmentCategory fixed = category("Fixed Income");
    InvestmentCategory crypto = category("Crypto");
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), fixed.getId(), "LCI"));
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), fixed.getId(), "CDB"));
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), variable.getId(), "ETFs"));

    List<InvestmentCategoryWithSubcategories> all = service.findAllWithSubcategories();

    assertThat(all)
        .extracting(c -> c.category().getName())
        .containsExactly("Crypto", "Fixed Income", "Variable Income");
    assertThat(all.get(0).category().getId()).isEqualTo(crypto.getId());
    assertThat(all.get(0).subcategories()).isEmpty();
    assertThat(all.get(1).subcategories())
        .extracting(InvestmentSubcategory::getName)
        .containsExactly("CDB", "LCI");
    assertThat(all.get(2).subcategories())
        .extracting(InvestmentSubcategory::getName)
        .containsExactly("ETFs");
  }

  @Test
  void findAllWithSubcategoriesReturnsEmptyListWhenNoCategoriesExist() {
    assertThat(service.findAllWithSubcategories()).isEmpty();
  }
}
