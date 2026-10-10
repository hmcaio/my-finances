package com.chm.myfinances.application.institution;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final AuditRecorder auditRecorder;

  public InstitutionService(
      InstitutionRepository institutionRepository,
      AccountRepository accountRepository,
      IdGenerator idGenerator,
      AuditRecorder auditRecorder) {
    this.institutionRepository = institutionRepository;
    this.accountRepository = accountRepository;
    this.idGenerator = idGenerator;
    this.auditRecorder = auditRecorder;
  }

  @Transactional
  public Institution create(String name) {
    if (institutionRepository.existsByName(name)) {
      throw new InstitutionNameAlreadyExistsException(name);
    }
    Institution institution = Institution.create(idGenerator.newId(), name);
    Institution saved = institutionRepository.save(institution);
    auditRecorder.recordCreate(
        AuditEntityType.INSTITUTION, saved.getId(), saved.getName(), saved.toAuditSnapshot());
    return saved;
  }

  /** Every institution, sorted by name (case-insensitive) - the list is small, so not paged. */
  public List<Institution> findAll() {
    return institutionRepository.findAll().stream()
        .sorted(Comparator.comparing(Institution::getName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional
  public Institution rename(UUID id, String newName) {
    Institution institution =
        institutionRepository.findById(id).orElseThrow(() -> new InstitutionNotFoundException(id));
    Map<String, Object> before = institution.toAuditSnapshot();
    if (institutionRepository.existsByNameAndIdNot(newName, id)) {
      throw new InstitutionNameAlreadyExistsException(newName);
    }
    institution.rename(newName);
    Institution saved = institutionRepository.save(institution);
    auditRecorder.recordUpdate(
        AuditEntityType.INSTITUTION,
        saved.getId(),
        saved.getName(),
        before,
        saved.toAuditSnapshot());
    return saved;
  }

  @Transactional
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
    auditRecorder.recordDelete(
        AuditEntityType.INSTITUTION,
        institution.getId(),
        institution.getName(),
        institution.toAuditSnapshot());
  }
}
