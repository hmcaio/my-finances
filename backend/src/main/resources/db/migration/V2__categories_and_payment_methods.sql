-- F002: Categories & Payment Methods.
--
-- Creates the `categories` and `payment_methods` tables and seeds the predefined starter data
-- (PRD S5.1/S5.2, F002 spec). Ids are generated with gen_random_uuid() (built into Postgres 13+,
-- no extension required) purely for this one-time seed — every id created afterward through the
-- application comes from the IdGenerator port instead (ADR 0005).

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
    (gen_random_uuid(), 'Groceries',      'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Rent',           'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Utilities',      'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Subscriptions',  'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Transport',      'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Dining',         'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Health',         'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Other',          'EXPENSE', now(), now()),
    (gen_random_uuid(), 'Salary',         'INCOME',  now(), now()),
    (gen_random_uuid(), 'Other Income',   'INCOME',  now(), now());

INSERT INTO payment_methods (id, name, created_at, last_modified_at) VALUES
    (gen_random_uuid(), 'Debit Card',  now(), now()),
    (gen_random_uuid(), 'Credit Card', now(), now()),
    (gen_random_uuid(), 'PIX',         now(), now()),
    (gen_random_uuid(), 'Cash',        now(), now());
