-- Security-audit fix: categories.name / payment_methods.name were unbounded `text` columns with
-- only a NOT NULL / non-blank constraint above them, so a client could POST a multi-megabyte
-- string as a "name". Narrows both to varchar(100), matching the new domain-layer invariant
-- (NameConstraints.MAX_NAME_LENGTH) and the DTOs' @Size(max = 100).
--
-- Safe, non-destructive narrowing: every row seeded by V2 is well under 100 characters.

ALTER TABLE categories ALTER COLUMN name TYPE varchar(100);
ALTER TABLE payment_methods ALTER COLUMN name TYPE varchar(100);
