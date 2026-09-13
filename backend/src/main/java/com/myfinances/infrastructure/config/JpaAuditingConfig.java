package com.myfinances.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables {@code @CreatedDate}/{@code @LastModifiedDate} auditing on {@code AuditableEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
