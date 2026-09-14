package com.chm.myfinances.domain.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link PaymentMethod} (PRD S5.2, F002 spec). Pure JUnit — no Spring
 * context, no database (ADR 0004).
 */
class PaymentMethodTest {

  @Test
  void createsWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    PaymentMethod paymentMethod = PaymentMethod.create(id, "Debit Card");

    assertThat(paymentMethod.getId()).isEqualTo(id);
    assertThat(paymentMethod.getName()).isEqualTo("Debit Card");
  }

  @Test
  void renameChangesName() {
    PaymentMethod paymentMethod = PaymentMethod.create(UUID.randomUUID(), "Debit Card");

    paymentMethod.rename("Debit Card (Itau)");

    assertThat(paymentMethod.getName()).isEqualTo("Debit Card (Itau)");
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> PaymentMethod.create(UUID.randomUUID(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsBlankName() {
    PaymentMethod paymentMethod = PaymentMethod.create(UUID.randomUUID(), "Debit Card");

    assertThatThrownBy(() -> paymentMethod.rename("")).isInstanceOf(IllegalArgumentException.class);
  }
}
