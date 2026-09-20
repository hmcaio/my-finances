package com.chm.myfinances.application.institution;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Institution}: create/rename/delete (F017 spec). New ids come from the {@link
 * IdGenerator} port (ADR 0005) - never generated ad hoc here or left to the database.
 *
 * <p>Create/rename reject a duplicate name (409, {@link InstitutionNameAlreadyExistsException}) -
 * exact match, case-sensitive, backed by {@code institutions.name UNIQUE}.
 *
 * <p>Delete, in order: unknown id (404), the built-in row (409, {@link
 * BuiltInInstitutionException}), an institution some account still references (409, {@link
 * InstitutionInUseException}). F008 adds the investment-account reference check when that table
 * exists.
 */
@Service
public class InstitutionService {

  private final InstitutionRepository institutionRepository;
  private final AccountRepository accountRepository;
  private final IdGenerator idGenerator;

  public InstitutionService(
      InstitutionRepository institutionRepository,
      AccountRepository accountRepository,
      IdGenerator idGenerator) {
    this.institutionRepository = institutionRepository;
    this.accountRepository = accountRepository;
    this.idGenerator = idGenerator;
  }

  public Institution create(String name) {
    if (institutionRepository.existsByName(name)) {
      throw new InstitutionNameAlreadyExistsException(name);
    }
    Institution institution = Institution.create(idGenerator.newId(), name);
    return institutionRepository.save(institution);
  }

  /** Every institution, sorted by name (case-insensitive) - the list is small, so not paged. */
  public List<Institution> findAll() {
    return institutionRepository.findAll().stream()
        .sorted(Comparator.comparing(Institution::getName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public Institution rename(UUID id, String newName) {
    Institution institution =
        institutionRepository.findById(id).orElseThrow(() -> new InstitutionNotFoundException(id));
    if (institutionRepository.existsByNameAndIdNot(newName, id)) {
      throw new InstitutionNameAlreadyExistsException(newName);
    }
    institution.rename(newName);
    return institutionRepository.save(institution);
  }

  public void delete(UUID id) {
    Institution institution =
        institutionRepository.findById(id).orElseThrow(() -> new InstitutionNotFoundException(id));
    if (institution.isBuiltIn()) {
      throw new BuiltInInstitutionException(id);
    }
    if (accountRepository.existsByInstitutionId(id)) {
      throw new InstitutionInUseException(id);
    }
    institutionRepository.deleteById(id);
  }
}
