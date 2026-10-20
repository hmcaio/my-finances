package com.chm.myfinances.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins the fixed fuel type set (F024 spec, ADR 0021): no custom/user-editable fuel types. */
class FuelTypeTest {

  @Test
  void hasExactlyTheFourFixedValues() {
    assertThat(FuelType.values())
        .containsExactly(
            FuelType.ETANOL,
            FuelType.ETANOL_ADITIVADO,
            FuelType.GASOLINA,
            FuelType.GASOLINA_ADITIVADA);
  }
}
