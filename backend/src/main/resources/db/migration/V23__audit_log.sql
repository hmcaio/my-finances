-- F025: Audit Log (ADR 0022, PRD S5.12/S6.12).
--
-- One append-only table for every committed write on every aggregate. No foreign key to the
-- audited entity - a deleted entity's entries must outlive the row (`entity_label` is a snapshot
-- of its name/description, so the entry stays readable). `occurred_at` is stamped by the adapter
-- from the injected Clock, never SQL `now()`, so tests control it (and it survives a restore
-- consistently with the rest of the data, same volume ADR 0015 already backs up).
--
-- `origin` is `USER`/`SYSTEM` only - there is no actor (no authentication, S3). `changes` is a
-- free-form `jsonb` diff ({field: {from, to}}); `entity_label` is bounded like every other
-- free-text field (root CLAUDE.md), capped at TextFieldConstraints.MAX_DESCRIPTION_LENGTH (150)
-- when derived from a description.
--
-- Indexes: `(occurred_at desc)` for the Activity page's newest-first list; `(entity_type,
-- entity_id)` for a future entity-scoped history link (out of scope for v1, PRD S5.12's
-- non-goals, but the index costs nothing to add now and the query shape is obvious).
--
-- Numbered V23: V22 (trade confirmation tax backfill) was the highest when this was written.

CREATE TABLE audit_log (
    id               uuid PRIMARY KEY,
    occurred_at      timestamptz NOT NULL,
    entity_type      varchar(40) NOT NULL,
    entity_id        uuid NOT NULL,
    entity_label     varchar(200),
    action           varchar(20) NOT NULL,
    origin           varchar(10) NOT NULL,
    changes          jsonb NOT NULL DEFAULT '{}',
    request_id       varchar(64),
    created_at       timestamptz NOT NULL,
    last_modified_at timestamptz NOT NULL,
    CONSTRAINT chk_audit_log_origin CHECK (origin IN ('USER', 'SYSTEM'))
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
