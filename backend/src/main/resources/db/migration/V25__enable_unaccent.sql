-- F037 (ADR 0030): enable the `unaccent` extension (PostgreSQL contrib, trusted) for the Ledger's
-- accent-insensitive text search: lower(unaccent(column)) LIKE lower(unaccent(pattern)). It is the
-- first extension the app depends on. No table, column or index changes: the search runs against
-- the existing `description` / `additional_notes` columns, and no index is added (unaccent() is
-- STABLE, not IMMUTABLE, so it cannot back an expression index without a wrapper function).
--
-- The repository calls unaccent() unqualified, so the extension must live in a schema on the
-- connection's search_path: `public`, since the app configures no custom schema.

CREATE EXTENSION IF NOT EXISTS unaccent;
