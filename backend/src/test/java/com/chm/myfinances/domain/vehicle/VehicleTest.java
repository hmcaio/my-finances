package com.chm.myfinances.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Vehicle} (F024 spec). Pure JUnit — no Spring context, no
 * database (ADR 0004), written before {@link Vehicle} itself per F024's plan.md. Mirrors {@code
 * PaymentMethodTest} — same flat-taxonomy shape (name-only, non-blank, capped at {@code
 * MAX_NAME_LENGTH}).
 */
class VehicleTest {

  @Test
  void createsWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    Vehicle vehicle = Vehicle.create(id, "Civic");

    assertThat(vehicle.getId()).isEqualTo(id);
    assertThat(vehicle.getName()).isEqualTo("Civic");
  }

  @Test
  void renameChangesName() {
    Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "Civic");

    vehicle.rename("Civic 2019");

    assertThat(vehicle.getName()).isEqualTo("Civic 2019");
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> Vehicle.create(UUID.randomUUID(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullName() {
    assertThatThrownBy(() -> Vehicle.create(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsBlankName() {
    Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "Civic");

    assertThatThrownBy(() -> vehicle.rename("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsNameAtMaxLength() {
    String maxLengthName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);

    Vehicle vehicle = Vehicle.create(UUID.randomUUID(), maxLengthName);

    assertThat(vehicle.getName()).isEqualTo(maxLengthName);
  }

  @Test
  void createRejectsNameOverMaxLength() {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> Vehicle.create(UUID.randomUUID(), tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsNameOverMaxLength() {
    Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "Civic");
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> vehicle.rename(tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRebuildsFromPersistedState() {
    UUID id = UUID.randomUUID();

    Vehicle vehicle = Vehicle.reconstitute(id, "Civic");

    assertThat(vehicle.getId()).isEqualTo(id);
    assertThat(vehicle.getName()).isEqualTo("Civic");
  }
}
