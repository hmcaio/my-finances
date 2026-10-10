package com.chm.myfinances.application.paymentmethod;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link PaymentMethod}: create/rename/delete (F002 spec). New ids come from the
 * {@link IdGenerator} port (ADR 0005).
 *
 * <p>Delete enforces F002 plan.md's referenced-by-transaction guard, same as {@code
 * CategoryService} - deferred until F004 (Transactions) existed to check against. Unlike {@code
 * CategoryService}'s equivalent guard, this one wasn't broadened in the post-F007 schema audit -
 * {@code payment_methods} has no other FK referencing it besides {@code transactions}.
 *
 * <p>Create/rename reject a duplicate name (409, {@link PaymentMethodNameAlreadyExistsException}) -
 * exact match, case-sensitive, backed by {@code payment_methods.name UNIQUE} ({@code
 * V10__db_constraint_hardening.sql}), added in the same audit.
 */
@Service
public class PaymentMethodService {

  private final PaymentMethodRepository paymentMethodRepository;
  private final TransactionRepository transactionRepository;
  private final IdGenerator idGenerator;
  private final AuditRecorder auditRecorder;

  public PaymentMethodService(
      PaymentMethodRepository paymentMethodRepository,
      TransactionRepository transactionRepository,
      IdGenerator idGenerator,
      AuditRecorder auditRecorder) {
    this.paymentMethodRepository = paymentMethodRepository;
    this.transactionRepository = transactionRepository;
    this.idGenerator = idGenerator;
    this.auditRecorder = auditRecorder;
  }

  @Transactional
  public PaymentMethod create(String name) {
    if (paymentMethodRepository.existsByName(name)) {
      throw new PaymentMethodNameAlreadyExistsException(name);
    }
    PaymentMethod paymentMethod = PaymentMethod.create(idGenerator.newId(), name);
    PaymentMethod saved = paymentMethodRepository.save(paymentMethod);
    auditRecorder.recordCreate(
        AuditEntityType.PAYMENT_METHOD, saved.getId(), saved.getName(), saved.toAuditSnapshot());
    return saved;
  }

  public List<PaymentMethod> findAll() {
    return paymentMethodRepository.findAll();
  }

  @Transactional
  public PaymentMethod rename(UUID id, String newName) {
    PaymentMethod paymentMethod =
        paymentMethodRepository
            .findById(id)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    Map<String, Object> before = paymentMethod.toAuditSnapshot();
    if (paymentMethodRepository.existsByNameAndIdNot(newName, id)) {
      throw new PaymentMethodNameAlreadyExistsException(newName);
    }
    paymentMethod.rename(newName);
    PaymentMethod saved = paymentMethodRepository.save(paymentMethod);
    auditRecorder.recordUpdate(
        AuditEntityType.PAYMENT_METHOD,
        saved.getId(),
        saved.getName(),
        before,
        saved.toAuditSnapshot());
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    PaymentMethod paymentMethod =
        paymentMethodRepository
            .findById(id)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    if (transactionRepository.existsByPaymentMethodId(id)) {
      throw new PaymentMethodInUseException(id);
    }
    paymentMethodRepository.deleteById(id);
    auditRecorder.recordDelete(
        AuditEntityType.PAYMENT_METHOD,
        paymentMethod.getId(),
        paymentMethod.getName(),
        paymentMethod.toAuditSnapshot());
  }
}
