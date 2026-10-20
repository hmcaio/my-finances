package com.chm.myfinances.domain.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link PaymentMethod} (PRD S5.2, F002 spec). Pure JUnit — no Spring
 * context, no database (ADR 0004).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename} were deleted — every row is now fixed,
 * migration-seeded content, reconstituted from persistence only. These tests now exercise {@link
 * PaymentMethod#reconstitute} directly for the same validation {@code create} used to cover.
 */
class PaymentMethodTest {

  @Test
  void reconstitutesWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    PaymentMethod paymentMethod = PaymentMethod.reconstitute(id, "Debit Card");

    assertThat(paymentMethod.getId()).isEqualTo(id);
    assertThat(paymentMethod.getName()).isEqualTo("Debit Card");
  }

  @Test
  void reconstituteRejectsBlankName() {
    assertThatThrownBy(() -> PaymentMethod.reconstitute(UUID.randomUUID(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteAcceptsNameAtMaxLength() {
    String maxLengthName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);

    PaymentMethod paymentMethod = PaymentMethod.reconstitute(UUID.randomUUID(), maxLengthName);

    assertThat(paymentMethod.getName()).isEqualTo(maxLengthName);
  }

  @Test
  void reconstituteRejectsNameOverMaxLength() {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> PaymentMethod.reconstitute(UUID.randomUUID(), tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
