package com.chm.myfinances.domain.transaction;

/**
 * Fixed set of fuel types a {@link FuelDetails} can record (F024 spec, ADR 0021). Deliberately not
 * a user-editable taxonomy like {@code Category}/{@code PaymentMethod}: there is no product need
 * yet for custom fuel types, and widening this enum later is additive, not breaking.
 */
public enum FuelType {
  ETANOL,
  ETANOL_ADITIVADO,
  GASOLINA,
  GASOLINA_ADITIVADA
}
