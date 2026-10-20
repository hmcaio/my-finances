-- F002: Categories & Payment Methods.
--
-- Creates the `categories` and `payment_methods` tables. Payment methods keep their predefined
-- starter seed (PRD S5.2). Categories seed only the two rows "Other"/"Other Income" that V14
-- later adopts and flags as built-in (PRD S5.1 §5.1) — the eight regular starter categories
-- (Groceries, Rent, Utilities, Subscriptions, Transport, Dining, Health, Salary) were removed from
-- this seed by F035/ADR 0028: Category is the one flat-taxonomy entity that's genuinely personal,
-- so its starter list is now offered through a localized onboarding step (F035) and inserted
-- through the ordinary create API instead of being fixed, English-only migration data. Ids are
-- generated with gen_random_uuid() (built into Postgres 13+, no extension required) purely for
-- this one-time seed — every id created afterward through the application comes from the
-- IdGenerator port instead (ADR 0005).

CREATE TABLE categories (
    id                uuid PRIMARY KEY,
    name              text NOT NULL,
    type              text NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL
);

CREATE TABLE payment_methods (
    id                uuid PRIMARY KEY,
    name              text NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL
);

INSERT INTO categories (id, name, type, created_at, last_modified_at) VALUES
    (gen_random_uuid(), 'Other',          'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Other Income',   'INCOME',  now(), now());

INSERT INTO payment_methods (id, name, created_at, last_modified_at) VALUES
    (gen_random_uuid(), 'Debit Card',  now(), now()),
    (gen_random_uuid(), 'Credit Card', now(), now()),
    (gen_random_uuid(), 'PIX',         now(), now()),
    (gen_random_uuid(), 'Cash',        now(), now());
