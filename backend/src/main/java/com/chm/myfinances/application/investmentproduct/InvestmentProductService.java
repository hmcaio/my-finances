package com.chm.myfinances.application.investmentproduct;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.investmentcategory.InvestmentCategoryNotFoundException;
import com.chm.myfinances.application.investmentsubcategory.InvestmentSubcategoryNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.HasInvestmentHistoryChecker;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentProduct}: create/edit/close/delete and the reads (F008 spec). New
 * ids come from the {@link IdGenerator} port (ADR 0005).
 *
 * <p>Create and edit (PATCH is a full replace, so it can reclassify or move a product) verify, in
 * order: the account exists (404) and is an open {@code INVESTMENT} account (409, {@link
 * InvestmentAccountRequiredException}); the category exists (404); the sub-category, when given,
 * exists (404) and belongs to that category (409, {@link InvestmentSubcategoryMismatchException});
 * and the name is unique within the account (409). Nothing here is multi-write, so nothing is
 * {@code @Transactional}. Delete is only allowed at zero history, as reported by the {@link
 * HasInvestmentHistoryChecker} port (F009 supplies the real answer); otherwise the user closes the
 * product instead.
 */
@Service
public class InvestmentProductService {

  private final InvestmentProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final InvestmentCategoryRepository categoryRepository;
  private final InvestmentSubcategoryRepository subcategoryRepository;
  private final HasInvestmentHistoryChecker historyChecker;
  private final IdGenerator idGenerator;
  private final Clock clock;

  public InvestmentProductService(
      InvestmentProductRepository productRepository,
      AccountRepository accountRepository,
      InvestmentCategoryRepository categoryRepository,
      InvestmentSubcategoryRepository subcategoryRepository,
      HasInvestmentHistoryChecker historyChecker,
      IdGenerator idGenerator,
      Clock clock) {
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.historyChecker = historyChecker;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  public InvestmentProduct create(
      UUID accountId, UUID investmentCategoryId, UUID investmentSubcategoryId, String name) {
    requireValidReferences(accountId, investmentCategoryId, investmentSubcategoryId);
    if (productRepository.existsByAccountIdAndName(accountId, name)) {
      throw new InvestmentProductNameAlreadyExistsException(name);
    }
    return productRepository.save(
        InvestmentProduct.create(
            idGenerator.newId(), accountId, investmentCategoryId, investmentSubcategoryId, name));
  }

  public InvestmentProduct findById(UUID id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new InvestmentProductNotFoundException(id));
  }

  /** Lists products, optionally only those of one account ({@code null} means all). */
  public List<InvestmentProduct> findAll(UUID accountId) {
    return accountId == null
        ? productRepository.findAll()
        : productRepository.findByAccountId(accountId);
  }

  public InvestmentProduct edit(
      UUID id,
      UUID accountId,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name) {
    InvestmentProduct product = findById(id);
    requireValidReferences(accountId, investmentCategoryId, investmentSubcategoryId);
    if (productRepository.existsByAccountIdAndNameAndIdNot(accountId, name, id)) {
      throw new InvestmentProductNameAlreadyExistsException(name);
    }
    product.edit(accountId, investmentCategoryId, investmentSubcategoryId, name);
    return productRepository.save(product);
  }

  /**
   * Closes a product. One write, so no {@code @Transactional}. F009 adds the guard that a product
   * can only be closed while its latest snapshot is {@code 0} or absent.
   */
  public InvestmentProduct close(UUID id) {
    InvestmentProduct product = findById(id);
    if (product.isClosed()) {
      throw new InvestmentProductAlreadyClosedException(id);
    }
    product.close(LocalDate.now(clock));
    return productRepository.save(product);
  }

  public void delete(UUID id) {
    findById(id);
    if (historyChecker.hasHistory(id)) {
      throw new InvestmentProductHasHistoryException(id);
    }
    productRepository.deleteById(id);
  }

  /** Whether the product has history - drives the detail response's {@code hasHistory} flag. */
  public boolean hasHistory(UUID id) {
    findById(id);
    return historyChecker.hasHistory(id);
  }

  private void requireValidReferences(
      UUID accountId, UUID investmentCategoryId, UUID investmentSubcategoryId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (account.getType() != AccountType.INVESTMENT || account.isClosed()) {
      throw new InvestmentAccountRequiredException(accountId);
    }
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
