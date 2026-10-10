package com.chm.myfinances.infrastructure.persistence.auditlog;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditEntry;
import com.chm.myfinances.application.auditlog.AuditLog;
import com.chm.myfinances.application.auditlog.AuditLogFilter;
import com.chm.myfinances.application.auditlog.AuditLogRecord;
import com.chm.myfinances.application.auditlog.AuditLogRepository;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.FieldChange;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.infrastructure.web.RequestLoggingFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing both {@link AuditLog} (write) and {@link AuditLogRepository} (read) on top
 * of Spring Data/Hibernate (ADR 0004, F025 spec's Persistence section).
 *
 * <p>{@link #record} stamps {@code occurredAt} from its own injected {@link Clock} - never SQL
 * {@code now()}, so tests control it - and {@code requestId} from the MDC ({@link
 * RequestLoggingFilter#MDC_KEY}, the same key F016 sets), never from the caller's {@link
 * AuditEntry} (ADR 0022: "taken from the MDC by the adapter, not by callers"). {@code changes} is
 * serialized to/from a JSON string with Jackson - {@link AuditLogJpaEntity#getChanges()} is a plain
 * {@code String} column mapped as {@code jsonb} via {@code @JdbcTypeCode(SqlTypes.JSON)}. There is
 * no {@code ObjectMapper} bean in this app (backend {@code CLAUDE.md}'s Testing section notes the
 * same for tests), so this builds its own private instance rather than trying to {@code @Autowired}
 * one.
 */
@Component
public class AuditLogRepositoryAdapter implements AuditLog, AuditLogRepository {

  private final AuditLogJpaRepository jpaRepository;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AuditLogRepositoryAdapter(
      AuditLogJpaRepository jpaRepository, IdGenerator idGenerator, Clock clock) {
    this.jpaRepository = jpaRepository;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  public void record(AuditEntry entry) {
    String requestId = MDC.get(RequestLoggingFilter.MDC_KEY);
    AuditLogJpaEntity entity =
        new AuditLogJpaEntity(
            idGenerator.newId(),
            Instant.now(clock),
            entry.entityType().name(),
            entry.entityId(),
            entry.entityLabel(),
            entry.action().name(),
            entry.origin().name(),
            writeChanges(entry.changes()),
            requestId);
    jpaRepository.save(entity);
  }

  @Override
  public Page<AuditLogRecord> findAll(AuditLogFilter filter, Pageable pageable) {
    return jpaRepository.findAll(toSpecification(filter), pageable).map(this::toRecord);
  }

  private static Specification<AuditLogJpaEntity> toSpecification(AuditLogFilter filter) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.from() != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), filter.to()));
      }
      if (filter.entityType() != null) {
        predicates.add(criteriaBuilder.equal(root.get("entityType"), filter.entityType().name()));
      }
      if (filter.action() != null) {
        predicates.add(criteriaBuilder.equal(root.get("action"), filter.action().name()));
      }
      if (filter.origin() != null) {
        predicates.add(criteriaBuilder.equal(root.get("origin"), filter.origin().name()));
      }
      if (filter.entityId() != null) {
        predicates.add(criteriaBuilder.equal(root.get("entityId"), filter.entityId()));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private AuditLogRecord toRecord(AuditLogJpaEntity entity) {
    return new AuditLogRecord(
        entity.getId(),
        entity.getOccurredAt(),
        AuditEntityType.valueOf(entity.getEntityType()),
        entity.getEntityId(),
        entity.getEntityLabel(),
        AuditAction.valueOf(entity.getAction()),
        AuditOrigin.valueOf(entity.getOrigin()),
        readChanges(entity.getChanges()),
        entity.getRequestId());
  }

  private String writeChanges(Map<String, FieldChange> changes) {
    try {
      return objectMapper.writeValueAsString(changes);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize audit changes", e);
    }
  }

  private Map<String, FieldChange> readChanges(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, FieldChange>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize audit changes", e);
    }
  }
}
