package com.chm.myfinances.application.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.testsupport.AccountMother;
import com.chm.myfinances.testsupport.FakeAccountRepository;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeInstitutionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InstitutionService}, written first (ADR 0004) against
 * hand-written fakes for {@link InstitutionRepository}/{@code IdGenerator} - plain JUnit, no Spring
 * context.
 *
 * <p>The delete guard's "in use" side is {@code AccountRepository.existsByInstitutionId}; F008 adds
 * the investment-account side when that table exists.
 */
class InstitutionServiceTest {

  private final FakeInstitutionRepository repository = new FakeInstitutionRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final InstitutionService service =
      new InstitutionService(repository, accountRepository, idGenerator);

  /** Stands in for the migration's seeded "No institution" row (only it can be built-in). */
  private Institution seedBuiltIn() {
    return repository.save(Institution.reconstitute(UUID.randomUUID(), "No institution", true));
  }

  private void accountAt(UUID institutionId, boolean closed) {
    Account account =
        AccountMother.checking()
            .withName("Account " + UUID.randomUUID())
            .withInstitutionId(institutionId)
            .build();
    if (closed) {
      account.close(LocalDate.now());
    }
    accountRepository.save(account);
  }

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    InstitutionService service =
        new InstitutionService(repository, accountRepository, new FakeIdGenerator(nextId));

    Institution created = service.create("Nubank");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Nubank");
    assertThat(created.isBuiltIn()).isFalse();
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Nubank");

    assertThatThrownBy(() -> service.create("Nubank"))
        .isInstanceOf(InstitutionNameAlreadyExistsException.class);
  }

  @Test
  void createRejectsTheBuiltInRowsName() {
    // Nobody can create a second "No institution" by name.
    seedBuiltIn();

    assertThatThrownBy(() -> service.create("No institution"))
        .isInstanceOf(InstitutionNameAlreadyExistsException.class);
  }

  @Test
  void findAllReturnsEveryPersistedInstitution() {
    service.create("Nubank");
    service.create("Itau");

    List<Institution> all = service.findAll();

    assertThat(all).extracting(Institution::getName).containsExactlyInAnyOrder("Nubank", "Itau");
  }

  @Test
  void findAllIsSortedByNameIgnoringCase() {
    service.create("nubank");
    service.create("Zed");
    service.create("Itau");
    seedBuiltIn();

    assertThat(service.findAll())
        .extracting(Institution::getName)
        .containsExactly("Itau", "No institution", "nubank", "Zed");
  }

  @Test
  void renameUpdatesTheName() {
    Institution created = service.create("Nubank");

    Institution renamed = service.rename(created.getId(), "Nu Pagamentos");

    assertThat(renamed.getName()).isEqualTo("Nu Pagamentos");
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    Institution created = service.create("Nubank");

    Institution renamed = service.rename(created.getId(), "Nubank");

    assertThat(renamed.getName()).isEqualTo("Nubank");
  }

  @Test
  void renameRejectsADuplicateName() {
    service.create("Nubank");
    Institution itau = service.create("Itau");

    assertThatThrownBy(() -> service.rename(itau.getId(), "Nubank"))
        .isInstanceOf(InstitutionNameAlreadyExistsException.class);
  }

  @Test
  void renameOfTheBuiltInRowIsAllowedAndKeepsItBuiltIn() {
    Institution builtIn = seedBuiltIn();

    Institution renamed = service.rename(builtIn.getId(), "Sem instituicao");

    assertThat(renamed.getName()).isEqualTo("Sem instituicao");
    assertThat(renamed.isBuiltIn()).isTrue();
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "New name"))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  void deleteRemovesAnUnreferencedInstitution() {
    Institution created = service.create("Temp");

    service.delete(created.getId());

    assertThat(repository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  void deleteOfTheBuiltInRowIsRejectedEvenWithZeroReferences() {
    Institution builtIn = seedBuiltIn();

    assertThatThrownBy(() -> service.delete(builtIn.getId()))
        .isInstanceOf(BuiltInInstitutionException.class);
    assertThat(repository.findById(builtIn.getId())).isPresent();
  }

  @Test
  void deleteOfTheBuiltInRowStaysRejectedAfterARename() {
    Institution builtIn = seedBuiltIn();
    service.rename(builtIn.getId(), "Sem instituicao");

    assertThatThrownBy(() -> service.delete(builtIn.getId()))
        .isInstanceOf(BuiltInInstitutionException.class);
  }

  @Test
  void deleteIsBlockedWhileAnOpenAccountReferencesTheInstitution() {
    Institution created = service.create("Nubank");
    accountAt(created.getId(), false);

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InstitutionInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void deleteIsBlockedWhileAClosedAccountReferencesTheInstitution() {
    // Accounts are never deleted, so a closed one still pins its institution.
    Institution created = service.create("Nubank");
    accountAt(created.getId(), true);

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InstitutionInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void deleteSucceedsOnceNoAccountReferencesTheInstitution() {
    Institution nubank = service.create("Nubank");
    Institution itau = service.create("Itau");
    accountAt(nubank.getId(), false);
    // The only account points elsewhere: Itau is unreferenced even though accounts exist.

    service.delete(itau.getId());

    assertThat(repository.findById(itau.getId())).isEmpty();
    assertThat(repository.findById(nubank.getId())).isPresent();
  }
}
