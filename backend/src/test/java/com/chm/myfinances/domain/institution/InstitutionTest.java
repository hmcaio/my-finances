package com.chm.myfinances.domain.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Institution} (PRD S5.10, F017 spec). Pure JUnit - no Spring
 * context, no database (ADR 0004) - written before {@link Institution} itself, per F017's plan.md.
 */
class InstitutionTest {

  @Test
  void createsWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    Institution institution = Institution.create(id, "Nubank");

    assertThat(institution.getId()).isEqualTo(id);
    assertThat(institution.getName()).isEqualTo("Nubank");
  }

  @Test
  void createAlwaysYieldsANonBuiltInInstitution() {
    Institution institution = Institution.create(UUID.randomUUID(), "Nubank");

    assertThat(institution.isBuiltIn()).isFalse();
  }

  @Test
  void reconstitutePreservesTheBuiltInFlag() {
    UUID id = UUID.randomUUID();

    Institution builtIn = Institution.reconstitute(id, "No institution", true);
    Institution regular = Institution.reconstitute(id, "Nubank", false);

    assertThat(builtIn.getId()).isEqualTo(id);
    assertThat(builtIn.getName()).isEqualTo("No institution");
    assertThat(builtIn.isBuiltIn()).isTrue();
    assertThat(regular.isBuiltIn()).isFalse();
  }

  @Test
  void renameChangesTheName() {
    Institution institution = Institution.create(UUID.randomUUID(), "Nubank");

    institution.rename("Nu Pagamentos");

    assertThat(institution.getName()).isEqualTo("Nu Pagamentos");
  }

  @Test
  void renameIsAllowedOnTheBuiltInInstitutionAndKeepsItBuiltIn() {
    Institution builtIn = Institution.reconstitute(UUID.randomUUID(), "No institution", true);

    builtIn.rename("Sem instituicao");

    assertThat(builtIn.getName()).isEqualTo("Sem instituicao");
    assertThat(builtIn.isBuiltIn()).isTrue();
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> Institution.create(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullName() {
    assertThatThrownBy(() -> Institution.create(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> Institution.create(null, "Nubank"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void renameRejectsBlankName() {
    Institution institution = Institution.create(UUID.randomUUID(), "Nubank");

    assertThatThrownBy(() -> institution.rename(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsBlankNameOnTheBuiltInInstitutionToo() {
    Institution builtIn = Institution.reconstitute(UUID.randomUUID(), "No institution", true);

    assertThatThrownBy(() -> builtIn.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(builtIn.getName()).isEqualTo("No institution");
  }

  @Test
  void createAcceptsNameAtMaxLength() {
    String maxLengthName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);

    Institution institution = Institution.create(UUID.randomUUID(), maxLengthName);

    assertThat(institution.getName()).isEqualTo(maxLengthName);
  }

  @Test
  void createRejectsNameOverMaxLength() {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> Institution.create(UUID.randomUUID(), tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsNameOverMaxLength() {
    Institution institution = Institution.create(UUID.randomUUID(), "Nubank");
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> institution.rename(tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsNameOverMaxLengthOnTheBuiltInInstitutionToo() {
    Institution builtIn = Institution.reconstitute(UUID.randomUUID(), "No institution", true);
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> builtIn.rename(tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
