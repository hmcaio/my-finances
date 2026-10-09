package com.chm.myfinances.infrastructure.persistence.auditlog;

import com.chm.myfinances.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping for the append-only {@code audit_log} table (F025 spec, ADR 0022). Extends {@link
 * AuditableEntity} (ArchUnit's {@code everyEntityExtendsAuditableEntity} rule) even though its
 * {@code createdAt}/{@code lastModifiedAt} are redundant with {@link #occurredAt} for a row that's
 * never updated - the row genuinely is append-only, so it fits that base class fine; it just never
 * uses {@code lastModifiedAt} for anything.
 *
 * <p>{@code changes} is stored as {@code jsonb}: {@code @JdbcTypeCode(SqlTypes.JSON)} on a plain
 * {@code String} field passes an already-serialized JSON string straight through to Postgres's
 * {@code jsonb} column, rather than Hibernate trying to serialize a {@code String} itself (which
 * would double-encode it) - {@link AuditLogRepositoryAdapter} does the Jackson
 * serialization/deserialization at the boundary.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogJpaEntity extends AuditableEntity {

  @Id private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "entity_type", nullable = false, length = 40)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "entity_label", length = 200)
  private String entityLabel;

  @Column(nullable = false, length = 20)
  private String action;

  @Column(nullable = false, length = 10)
  private String origin;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String changes;

  @Column(name = "request_id", length = 64)
  private String requestId;

  public AuditLogJpaEntity(
      UUID id,
      Instant occurredAt,
      String entityType,
      UUID entityId,
      String entityLabel,
      String action,
      String origin,
      String changes,
      String requestId) {
    this.id = id;
    this.occurredAt = occurredAt;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityLabel = entityLabel;
    this.action = action;
    this.origin = origin;
    this.changes = changes;
    this.requestId = requestId;
  }
}
