package com.chm.myfinances.infrastructure.persistence.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration test for {@link InstitutionRepositoryAdapter}: hits a real,
 * ephemeral Postgres via Testcontainers (ADR 0010), so {@code V12__institutions.sql} runs for real
 * too - including its seeded built-in "No institution" row, which exists before any test runs and
 * which rollback never removes. Fixture names therefore use the {@code " Test"} suffix, and nothing
 * here assumes {@code findAll()} is empty or of a fixed size.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InstitutionRepositoryAdapterTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void savesAndReloadsAnInstitution() {
    Institution institution = Institution.create(UUID.randomUUID(), "Nubank Test");

    institutionRepository.save(institution);

    Optional<Institution> reloaded = institutionRepository.findById(institution.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Nubank Test");
    assertThat(reloaded.get().isBuiltIn()).isFalse();
  }

  @Test
  void renamePersists() {
    Institution institution = Institution.create(UUID.randomUUID(), "Original Test");
    institutionRepository.save(institution);

    institution.rename("Renamed Test");
    institutionRepository.save(institution);

    Optional<Institution> reloaded = institutionRepository.findById(institution.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed Test");
  }

  @Test
  void deleteByIdRemovesTheInstitution() {
    Institution institution = Institution.create(UUID.randomUUID(), "Temp Test");
    institutionRepository.save(institution);

    institutionRepository.deleteById(institution.getId());

    assertThat(institutionRepository.findById(institution.getId())).isEmpty();
  }

  @Test
  void findAllReturnsEveryInstitution() {
    institutionRepository.save(Institution.create(UUID.randomUUID(), "Institution A Test"));
    institutionRepository.save(Institution.create(UUID.randomUUID(), "Institution B Test"));

    assertThat(institutionRepository.findAll())
        .extracting(Institution::getName)
        .contains("Institution A Test", "Institution B Test");
  }

  @Test
  void existsByIdReflectsPersistedState() {
    Institution institution = Institution.create(UUID.randomUUID(), "Exists Test");

    assertThat(institutionRepository.existsById(institution.getId())).isFalse();

    institutionRepository.save(institution);

    assertThat(institutionRepository.existsById(institution.getId())).isTrue();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    institutionRepository.save(Institution.create(UUID.randomUUID(), "Unique Name Test"));

    assertThat(institutionRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(institutionRepository.existsByName("unique name test")).isFalse();
    assertThat(institutionRepository.existsByName("Something Else Test")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    Institution institution =
        institutionRepository.save(Institution.create(UUID.randomUUID(), "Exclude Self Test"));

    assertThat(institutionRepository.existsByNameAndIdNot("Exclude Self Test", institution.getId()))
        .isFalse();
    assertThat(institutionRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void theDatabaseRejectsADuplicateNameEvenViaAPlainSave() {
    institutionRepository.save(Institution.create(UUID.randomUUID(), "Duplicate Test"));
    entityManager.flush();
    institutionRepository.save(Institution.create(UUID.randomUUID(), "Duplicate Test"));

    assertThatThrownBy(entityManager::flush).hasMessageContaining("uq_institutions_name");
  }

  @Test
  void theBuiltInInstitutionIsSeededByTheMigrationAndIsTheOnlyOne() {
    assertThat(institutionRepository.findAll().stream().filter(Institution::isBuiltIn))
        .singleElement()
        .satisfies(
            builtIn -> {
              assertThat(builtIn.getName()).isEqualTo("No institution");
              assertThat(builtIn.getId()).isNotNull();
            });
  }

  @Test
  void theBuiltInInstitutionCanBeRenamedAndStaysBuiltIn() {
    Institution builtIn =
        institutionRepository.findAll().stream().filter(Institution::isBuiltIn).findFirst().get();

    builtIn.rename("Sem instituicao Test");
    institutionRepository.save(builtIn);
    entityManager.flush();
    entityManager.clear();

    Institution reloaded = institutionRepository.findById(builtIn.getId()).get();
    assertThat(reloaded.getName()).isEqualTo("Sem instituicao Test");
    assertThat(reloaded.isBuiltIn()).isTrue();
  }

  @Test
  void aSecondBuiltInRowIsRejectedByThePartialUniqueIndex() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)"
                        + " VALUES (?, 'Second Built-in Test', true, now(), now())",
                    UUID.randomUUID()))
        .hasMessageContaining("uq_institutions_single_built_in");
  }

  @Test
  void applicationSavesNeverProduceABuiltInRow() {
    Institution institution = Institution.create(UUID.randomUUID(), "Never Built-in Test");

    institutionRepository.save(institution);
    entityManager.flush();

    Boolean builtIn =
        jdbc.queryForObject(
            "SELECT built_in FROM institutions WHERE id = ?", Boolean.class, institution.getId());
    assertThat(builtIn).isFalse();
  }
}
