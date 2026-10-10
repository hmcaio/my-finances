package com.chm.myfinances.infrastructure.persistence.auditlog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Package-private Spring Data repository for {@link AuditLogJpaEntity} (backend {@code CLAUDE.md}).
 */
interface AuditLogJpaRepository
    extends JpaRepository<AuditLogJpaEntity, UUID>, JpaSpecificationExecutor<AuditLogJpaEntity> {}
