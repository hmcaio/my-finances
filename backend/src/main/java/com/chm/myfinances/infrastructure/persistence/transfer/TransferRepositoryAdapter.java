package com.chm.myfinances.infrastructure.persistence.transfer;

import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
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
 * Adapter implementing the domain's {@link TransferRepository} port on top of Spring Data/
 * Hibernate (ADR 0004). Translates between the framework-free {@link Transfer} aggregate and {@link
 * TransferJpaEntity} - same shape as {@code TransactionRepositoryAdapter}.
 */
@Component
public class TransferRepositoryAdapter implements TransferRepository {

  private final TransferJpaRepository jpaRepository;

  public TransferRepositoryAdapter(TransferJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Transfer save(Transfer transfer) {
    TransferJpaEntity entity =
        jpaRepository
            .findById(transfer.getId())
            .map(
                existing -> {
                  existing.setDate(transfer.getDate());
                  existing.setFromAccountId(transfer.getFromAccountId());
                  existing.setToAccountId(transfer.getToAccountId());
                  existing.setAmount(transfer.getAmount());
                  existing.setDescription(transfer.getDescription());
                  existing.setAdditionalNotes(transfer.getAdditionalNotes());
                  existing.setInvestmentProductId(transfer.getInvestmentProductId());
                  existing.setQuantity(transfer.getTradeDetails().quantity());
                  existing.setUnitPrice(transfer.getTradeDetails().unitPrice());
                  existing.setTaxes(transfer.getTradeDetails().taxes());
                  return existing;
                })
            .orElseGet(
                () ->
                    new TransferJpaEntity(
                        transfer.getId(),
                        transfer.getDate(),
                        transfer.getFromAccountId(),
                        transfer.getToAccountId(),
                        transfer.getAmount(),
                        transfer.getDescription(),
                        transfer.getAdditionalNotes(),
                        transfer.getInvestmentProductId(),
                        transfer.getTradeDetails().quantity(),
                        transfer.getTradeDetails().unitPrice(),
                        transfer.getTradeDetails().taxes()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Transfer> findById(UUID id) {
    return jpaRepository.findById(id).map(TransferRepositoryAdapter::toDomain);
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
  public Page<Transfer> findAll(TransferFilter filter, Pageable pageable) {
    return jpaRepository
        .findAll(toSpecification(filter), pageable)
        .map(TransferRepositoryAdapter::toDomain);
  }

  @Override
  public List<Transfer> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate) {
    return jpaRepository.findByAccountIdOnOrBefore(accountId, asOfDate).stream()
        .map(TransferRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<LocalDate> findDistinctDatesBetween(LocalDate from, LocalDate to) {
    return jpaRepository.findDistinctDatesBetween(from, to);
  }

  @Override
  public List<Transfer> findByInvestmentProductId(UUID investmentProductId) {
    return jpaRepository.findByInvestmentProductId(investmentProductId).stream()
        .map(TransferRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Transfer> findAllInvestmentTrades() {
    return jpaRepository.findByInvestmentProductIdIsNotNull().stream()
        .map(TransferRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByInvestmentProductId(UUID investmentProductId) {
    return jpaRepository.existsByInvestmentProductId(investmentProductId);
  }

  private static Specification<TransferJpaEntity> toSpecification(TransferFilter filter) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.dateFrom() != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), filter.dateFrom()));
      }
      if (filter.dateTo() != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), filter.dateTo()));
      }
      if (filter.accountId() != null) {
        predicates.add(
            criteriaBuilder.or(
                criteriaBuilder.equal(root.get("fromAccountId"), filter.accountId()),
                criteriaBuilder.equal(root.get("toAccountId"), filter.accountId())));
      }
      if (filter.investmentProductId() != null) {
        predicates.add(
            criteriaBuilder.equal(root.get("investmentProductId"), filter.investmentProductId()));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Transfer toDomain(TransferJpaEntity entity) {
    return Transfer.reconstitute(
        entity.getId(),
        entity.getDate(),
        entity.getFromAccountId(),
        entity.getToAccountId(),
        entity.getAmount(),
        entity.getDescription(),
        entity.getAdditionalNotes(),
        entity.getInvestmentProductId(),
        new InvestmentTradeDetails(entity.getQuantity(), entity.getUnitPrice(), entity.getTaxes()));
  }
}
