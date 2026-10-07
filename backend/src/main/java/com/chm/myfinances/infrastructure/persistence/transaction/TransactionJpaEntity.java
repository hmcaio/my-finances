package com.chm.myfinances.infrastructure.persistence.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for the {@code transactions} table (F004 spec). Foreign entities (category, account,
 * payment method) are referenced by plain {@code UUID} columns, not JPA associations - same
 * standalone-aggregate style as {@code AccountJpaEntity}/{@code CategoryJpaEntity}.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class TransactionJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CategoryType type;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "payment_method_id", nullable = false)
  private UUID paymentMethodId;

  @Column(name = "recurring_template_version_id")
  private UUID recurringTemplateVersionId;

  @Column(nullable = false)
  private String description;

  @Column(name = "additional_notes")
  private String additionalNotes;

  // F024 (ADR 0021): flat columns mirroring TransferJpaEntity's InvestmentTradeDetails style -
  // vehicleId/fuelType/liters/pricePerLiter are all null together (no fuel purchase) or all
  // non-null together; kmSinceLastFill/odometer are independently optional.
  @Column(name = "vehicle_id")
  private UUID vehicleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "fuel_type", length = 30)
  private FuelType fuelType;

  private BigDecimal liters;

  @Column(name = "price_per_liter")
  private BigDecimal pricePerLiter;

  @Column(name = "km_since_last_fill")
  private BigDecimal kmSinceLastFill;

  private BigDecimal odometer;

  public TransactionJpaEntity(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes,
      UUID vehicleId,
      FuelType fuelType,
      BigDecimal liters,
      BigDecimal pricePerLiter,
      BigDecimal kmSinceLastFill,
      BigDecimal odometer) {
    this.id = id;
    this.date = date;
    this.amount = amount;
    this.categoryId = categoryId;
    this.type = type;
    this.accountId = accountId;
    this.paymentMethodId = paymentMethodId;
    this.recurringTemplateVersionId = recurringTemplateVersionId;
    this.description = description;
    this.additionalNotes = additionalNotes;
    this.vehicleId = vehicleId;
    this.fuelType = fuelType;
    this.liters = liters;
    this.pricePerLiter = pricePerLiter;
    this.kmSinceLastFill = kmSinceLastFill;
    this.odometer = odometer;
  }
}
