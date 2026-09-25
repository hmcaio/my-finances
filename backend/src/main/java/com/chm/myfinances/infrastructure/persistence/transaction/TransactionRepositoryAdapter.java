package com.chm.myfinances.infrastructure.persistence.transaction;

import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link TransactionRepository} port on top of Spring Data/
 * Hibernate (ADR 0004). Translates between the framework-free {@link Transaction} aggregate and
 * {@link TransactionJpaEntity}.
 */
@Component
public class TransactionRepositoryAdapter implements TransactionRepository {

  private final TransactionJpaRepository jpaRepository;

  public TransactionRepositoryAdapter(TransactionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Transaction save(Transaction transaction) {
    TransactionJpaEntity entity =
        jpaRepository
            .findById(transaction.getId())
            .map(
                existing -> {
                  existing.setDate(transaction.getDate());
                  existing.setAmount(transaction.getAmount());
                  existing.setCategoryId(transaction.getCategoryId());
                  existing.setType(transaction.getType());
                  existing.setAccountId(transaction.getAccountId());
                  existing.setPaymentMethodId(transaction.getPaymentMethodId());
                  existing.setDescription(transaction.getDescription());
                  existing.setAdditionalNotes(transaction.getAdditionalNotes());
                  return existing;
                })
            .orElseGet(
                () ->
                    new TransactionJpaEntity(
                        transaction.getId(),
                        transaction.getDate(),
                        transaction.getAmount(),
                        transaction.getCategoryId(),
                        transaction.getType(),
                        transaction.getAccountId(),
                        transaction.getPaymentMethodId(),
                        transaction.getRecurringTemplateVersionId(),
                        transaction.getDescription(),
                        transaction.getAdditionalNotes()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Transaction> findById(UUID id) {
    return jpaRepository.findById(id).map(TransactionRepositoryAdapter::toDomain);
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return jpaRepository.existsById(id);
  }

  @Override
  public Page<Transaction> findAll(TransactionFilter filter, Pageable pageable) {
    return jpaRepository
        .findAll(toSpecification(filter), pageable)
        .map(TransactionRepositoryAdapter::toDomain);
  }

  @Override
  public List<Transaction> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate) {
    return jpaRepository.findByAccountIdAndDateLessThanEqual(accountId, asOfDate).stream()
        .map(TransactionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<LocalDate> findDistinctDatesBetween(LocalDate from, LocalDate to) {
    return jpaRepository.findDistinctDatesBetween(from, to);
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return jpaRepository.existsByCategoryId(categoryId);
  }

  @Override
  public boolean existsByPaymentMethodId(UUID paymentMethodId) {
    return jpaRepository.existsByPaymentMethodId(paymentMethodId);
  }

  @Override
  public boolean existsByAccountId(UUID accountId) {
    return jpaRepository.existsByAccountId(accountId);
  }

  private static Specification<TransactionJpaEntity> toSpecification(TransactionFilter filter) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.dateFrom() != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), filter.dateFrom()));
      }
      if (filter.dateTo() != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), filter.dateTo()));
      }
      if (filter.categoryId() != null) {
        predicates.add(criteriaBuilder.equal(root.get("categoryId"), filter.categoryId()));
      }
      if (filter.accountId() != null) {
        predicates.add(criteriaBuilder.equal(root.get("accountId"), filter.accountId()));
      }
      if (filter.paymentMethodId() != null) {
        predicates.add(
            criteriaBuilder.equal(root.get("paymentMethodId"), filter.paymentMethodId()));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Transaction toDomain(TransactionJpaEntity entity) {
    return Transaction.reconstitute(
        entity.getId(),
        entity.getDate(),
        entity.getAmount(),
        entity.getCategoryId(),
        entity.getType(),
        entity.getAccountId(),
        entity.getPaymentMethodId(),
        entity.getRecurringTemplateVersionId(),
        entity.getDescription(),
        entity.getAdditionalNotes());
  }
}
