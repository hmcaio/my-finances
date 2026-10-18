package com.chm.myfinances.infrastructure.persistence.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
  private final EntityManager entityManager;

  public TransactionRepositoryAdapter(
      TransactionJpaRepository jpaRepository, EntityManager entityManager) {
    this.jpaRepository = jpaRepository;
    this.entityManager = entityManager;
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
                  applyFuelDetails(existing, transaction.getFuelDetails());
                  existing.setInvestmentHoldingId(transaction.getInvestmentHoldingId());
                  return existing;
                })
            .orElseGet(() -> newEntity(transaction));
    return toDomain(jpaRepository.save(entity));
  }

  private static TransactionJpaEntity newEntity(Transaction transaction) {
    FuelDetails fuel = transaction.getFuelDetails();
    return new TransactionJpaEntity(
        transaction.getId(),
        transaction.getDate(),
        transaction.getAmount(),
        transaction.getCategoryId(),
        transaction.getType(),
        transaction.getAccountId(),
        transaction.getPaymentMethodId(),
        transaction.getRecurringTemplateVersionId(),
        transaction.getDescription(),
        transaction.getAdditionalNotes(),
        fuel == null ? null : fuel.vehicleId(),
        fuel == null ? null : fuel.fuelType(),
        fuel == null ? null : fuel.liters(),
        fuel == null ? null : fuel.pricePerLiter(),
        fuel == null ? null : fuel.kmSinceLastFill(),
        fuel == null ? null : fuel.odometer(),
        transaction.getInvestmentHoldingId());
  }

  private static void applyFuelDetails(TransactionJpaEntity entity, FuelDetails fuel) {
    entity.setVehicleId(fuel == null ? null : fuel.vehicleId());
    entity.setFuelType(fuel == null ? null : fuel.fuelType());
    entity.setLiters(fuel == null ? null : fuel.liters());
    entity.setPricePerLiter(fuel == null ? null : fuel.pricePerLiter());
    entity.setKmSinceLastFill(fuel == null ? null : fuel.kmSinceLastFill());
    entity.setOdometer(fuel == null ? null : fuel.odometer());
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

  /**
   * Grouped criteria query over the same {@link #toSpecification} predicate the list uses, so the
   * Ledger's rows and its summary can never disagree on a filter (F037).
   */
  @Override
  public Map<CategoryType, BigDecimal> sumAmountByType(TransactionFilter filter) {
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
    Root<TransactionJpaEntity> root = query.from(TransactionJpaEntity.class);
    query
        .multiselect(
            root.<CategoryType>get("type").alias("type"),
            criteriaBuilder.sum(root.<BigDecimal>get("amount")).alias("total"))
        .where(toSpecification(filter).toPredicate(root, query, criteriaBuilder))
        .groupBy(root.get("type"));
    Map<CategoryType, BigDecimal> totals = new EnumMap<>(CategoryType.class);
    for (Tuple row : entityManager.createQuery(query).getResultList()) {
      totals.put(row.get("type", CategoryType.class), row.get("total", BigDecimal.class));
    }
    return totals;
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

  @Override
  public boolean existsByFuelDetailsVehicleId(UUID vehicleId) {
    return jpaRepository.existsByVehicleId(vehicleId);
  }

  @Override
  public boolean existsByInvestmentHoldingId(UUID holdingId) {
    return jpaRepository.existsByInvestmentHoldingId(holdingId);
  }

  @Override
  public List<Transaction> findByCategoryIdAndDateRange(
      UUID categoryId, LocalDate from, LocalDate to) {
    Specification<TransactionJpaEntity> spec =
        (root, query, criteriaBuilder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(criteriaBuilder.equal(root.get("categoryId"), categoryId));
          if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), from));
          }
          if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), to));
          }
          return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    return jpaRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "date")).stream()
        .map(TransactionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Transaction> findByVehicleId(UUID vehicleId, LocalDate from, LocalDate to) {
    Specification<TransactionJpaEntity> spec =
        (root, query, criteriaBuilder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(criteriaBuilder.equal(root.get("vehicleId"), vehicleId));
          if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), from));
          }
          if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), to));
          }
          return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    return jpaRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "date")).stream()
        .map(TransactionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public BigDecimal sumAmountByCategoryAndDateRange(UUID categoryId, LocalDate from, LocalDate to) {
    return jpaRepository.sumAmountByCategoryAndDateRange(categoryId, from, to);
  }

  @Override
  public Map<UUID, BigDecimal> sumExpenseAmountByCategoryForDateRange(
      LocalDate from, LocalDate to) {
    return jpaRepository.sumExpenseAmountByCategoryForDateRange(from, to).stream()
        .collect(
            Collectors.toMap(
                CategoryTotalProjection::getCategoryId, CategoryTotalProjection::getTotal));
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
      if (filter.type() != null) {
        predicates.add(criteriaBuilder.equal(root.get("type"), filter.type()));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Transaction toDomain(TransactionJpaEntity entity) {
    FuelDetails fuelDetails =
        entity.getVehicleId() == null
            ? null
            : new FuelDetails(
                entity.getVehicleId(),
                entity.getFuelType(),
                entity.getLiters(),
                entity.getPricePerLiter(),
                entity.getKmSinceLastFill(),
                entity.getOdometer());
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
        entity.getAdditionalNotes(),
        fuelDetails,
        entity.getInvestmentHoldingId());
  }
}
