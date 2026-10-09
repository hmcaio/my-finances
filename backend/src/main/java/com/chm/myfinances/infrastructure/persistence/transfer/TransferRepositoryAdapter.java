package com.chm.myfinances.infrastructure.persistence.transfer;

import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing the domain's {@link TransferRepository} port on top of Spring Data/
 * Hibernate (ADR 0004). Translates between the framework-free {@link Transfer} aggregate and two
 * tables: {@code transfers} ({@link TransferJpaEntity}) and - F027, ADR 0024 - {@code
 * transfer_trade_lines} ({@link TransferTradeLineJpaEntity}) for a {@link TradeConfirmation}'s
 * lines, replaced wholesale on every save the same way {@code
 * AllocationPlanVersionRepositoryAdapter} replaces {@code AllocationPlanEntry} rows - a value
 * object with no id of its own, so each line gets a fresh id from {@link IdGenerator} (ADR 0005)
 * every save.
 */
@Component
public class TransferRepositoryAdapter implements TransferRepository {

  private final TransferJpaRepository jpaRepository;
  private final TransferTradeLineJpaRepository lineJpaRepository;
  private final IdGenerator idGenerator;

  public TransferRepositoryAdapter(
      TransferJpaRepository jpaRepository,
      TransferTradeLineJpaRepository lineJpaRepository,
      IdGenerator idGenerator) {
    this.jpaRepository = jpaRepository;
    this.lineJpaRepository = lineJpaRepository;
    this.idGenerator = idGenerator;
  }

  @Override
  @Transactional
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
                  existing.setTaxes(transfer.getTaxes());
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
                        transfer.getTaxes()));
    TransferJpaEntity saved = jpaRepository.save(entity);

    lineJpaRepository.deleteByTransferId(transfer.getId());
    List<TransferTradeLineJpaEntity> savedLines = new ArrayList<>();
    for (TradeConfirmationLine line :
        transfer.getTradeConfirmation().map(TradeConfirmation::getLines).orElse(List.of())) {
      savedLines.add(
          lineJpaRepository.save(
              new TransferTradeLineJpaEntity(
                  idGenerator.newId(),
                  transfer.getId(),
                  line.productId(),
                  line.side(),
                  line.quantity(),
                  line.unitPrice(),
                  line.resultingBalance(),
                  line.closeHolding())));
    }

    return toDomain(saved, savedLines);
  }

  @Override
  public Optional<Transfer> findById(UUID id) {
    return jpaRepository
        .findById(id)
        .map(entity -> toDomain(entity, lineJpaRepository.findByTransferId(id)));
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    lineJpaRepository.deleteByTransferId(id);
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
        .map(entity -> toDomain(entity, lineJpaRepository.findByTransferId(entity.getId())));
  }

  @Override
  public List<Transfer> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate) {
    return jpaRepository.findByAccountIdOnOrBefore(accountId, asOfDate).stream()
        .map(entity -> toDomain(entity, lineJpaRepository.findByTransferId(entity.getId())))
        .toList();
  }

  @Override
  public List<LocalDate> findDistinctDatesBetween(LocalDate from, LocalDate to) {
    return jpaRepository.findDistinctDatesBetween(from, to);
  }

  @Override
  public boolean existsByAccountId(UUID accountId) {
    return jpaRepository.existsByFromAccountIdOrToAccountId(accountId, accountId);
  }

  /**
   * {@code investmentProductId} (F009, kept by F027 for callers wanting whole-confirmation context)
   * now matches against {@code transfer_trade_lines.product_id} via a subquery - the flat column it
   * used to filter on is gone.
   */
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
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<TransferTradeLineJpaEntity> lineRoot = subquery.from(TransferTradeLineJpaEntity.class);
        subquery
            .select(lineRoot.get("transferId"))
            .where(criteriaBuilder.equal(lineRoot.get("productId"), filter.investmentProductId()));
        predicates.add(root.get("id").in(subquery));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Transfer toDomain(
      TransferJpaEntity entity, List<TransferTradeLineJpaEntity> lineEntities) {
    TradeConfirmation confirmation =
        lineEntities.isEmpty()
            ? null
            : TradeConfirmation.of(
                lineEntities.stream()
                    .map(
                        l ->
                            new TradeConfirmationLine(
                                l.getProductId(),
                                l.getSide(),
                                l.getQuantity(),
                                l.getUnitPrice(),
                                l.getResultingBalance(),
                                l.isCloseHolding()))
                    .toList());
    return Transfer.reconstitute(
        entity.getId(),
        entity.getDate(),
        entity.getFromAccountId(),
        entity.getToAccountId(),
        entity.getAmount(),
        entity.getDescription(),
        entity.getAdditionalNotes(),
        entity.getTaxes(),
        confirmation);
  }
}
