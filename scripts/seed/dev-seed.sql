-- Optional sample data for the dev stack (run it with scripts/seed-dev.sh; docs/conventions/tooling.md).
-- NOT a Flyway migration: nothing applies it automatically, and it is not packaged into any image.
--
-- Covers the core ledger: institution, categories, four accounts (checking, savings, cash wallet,
-- credit card), ~4 months of transactions, credit-card payment and savings transfers, and budgets
-- (with one cap change). Investments, vehicles/fuel and recurring templates are left out.
--
-- Every date is relative to current_date, so the data always looks recent. Nothing is random:
-- re-seeding an emptied database gives the same shape. The ids come from gen_random_uuid(), like
-- the migrations' own seeds. Reference rows the migrations already create (payment methods, the
-- built-in "Other Expense"/"Other Income" categories) are reused by name, never duplicated.
--
-- Refuses to run unless accounts, transactions and transfers are all empty, so it can never mix
-- sample rows into data you entered. One transaction: it either seeds everything or nothing.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM accounts) OR EXISTS (SELECT 1 FROM transactions) OR EXISTS (SELECT 1 FROM transfers) THEN
        RAISE EXCEPTION 'dev seed refused: accounts, transactions or transfers already hold data (empty the database first)';
    END IF;
END $$;

INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)
VALUES (gen_random_uuid(), 'Sample Bank', false, now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (id, name, type, created_at, last_modified_at)
SELECT gen_random_uuid(), c.name, c.type, now(), now()
FROM (VALUES
    ('Salary',        'INCOME'),
    ('Freelance',     'INCOME'),
    ('Groceries',     'EXPENSE'),
    ('Rent',          'EXPENSE'),
    ('Utilities',     'EXPENSE'),
    ('Subscriptions', 'EXPENSE'),
    ('Transport',     'EXPENSE'),
    ('Dining',        'EXPENSE'),
    ('Health',        'EXPENSE'),
    ('Leisure',       'EXPENSE')
) AS c(name, type)
ON CONFLICT (name) DO NOTHING;

-- Opening balances sit just before the oldest sample transaction (the first day of the month three
-- months back), so the first month is not a negative jump.
INSERT INTO accounts (id, name, type, opening_balance, opening_balance_date, institution_id, created_at, last_modified_at)
SELECT gen_random_uuid(), a.name, a.type, a.opening_balance,
       (date_trunc('month', current_date) - interval '3 months')::date,
       (SELECT id FROM institutions WHERE name = 'Sample Bank'), now(), now()
FROM (VALUES
    ('Main Checking', 'CHECKING',    3500.00),
    ('Savings',       'SAVINGS',     8000.00),
    ('Wallet',        'CASH_WALLET',  150.00),
    ('Credit Card',   'CREDIT_CARD',    0.00)
) AS a(name, type, opening_balance);

-- One row per month-offset (0 = this month .. 3 = three months back) x recurring line below. The
-- day is clamped to 1..28 so it exists in every month; lines dated after today are dropped.
INSERT INTO transactions (id, date, amount, category_id, type, account_id, payment_method_id,
                          description, additional_notes, created_at, last_modified_at)
SELECT gen_random_uuid(),
       (date_trunc('month', current_date) - make_interval(months => m.n) + make_interval(days => t.day - 1))::date,
       t.amount,
       (SELECT id FROM categories WHERE name = t.category),
       t.type,
       (SELECT id FROM accounts WHERE name = t.account),
       (SELECT id FROM payment_methods WHERE name = t.payment_method),
       t.description, t.notes, now(), now()
FROM generate_series(0, 3) AS m(n)
CROSS JOIN (VALUES
    -- day, description,               notes, category,        type,      account,         payment method, amount
    (5,  'Monthly salary',             NULL,                   'Salary',        'INCOME',  'Main Checking', 'PIX',          6200.00),
    (18, 'Freelance project',          'Invoice paid by PIX',  'Freelance',     'INCOME',  'Main Checking', 'PIX',           450.00),
    (10, 'Apartment rent',             NULL,                   'Rent',          'EXPENSE', 'Main Checking', 'PIX',          1800.00),
    (12, 'Electricity bill',           NULL,                   'Utilities',     'EXPENSE', 'Main Checking', 'PIX',           185.40),
    (13, 'Internet',                   NULL,                   'Utilities',     'EXPENSE', 'Main Checking', 'PIX',           119.90),
    (3,  'Supermarket',                NULL,                   'Groceries',     'EXPENSE', 'Main Checking', 'Debit Card',    212.35),
    (11, 'Supermarket',                NULL,                   'Groceries',     'EXPENSE', 'Main Checking', 'Debit Card',    187.60),
    (19, 'Supermarket',                NULL,                   'Groceries',     'EXPENSE', 'Main Checking', 'Debit Card',    234.10),
    (26, 'Farmers market',             NULL,                   'Groceries',     'EXPENSE', 'Wallet',        'Cash',           68.00),
    (7,  'Bus pass top-up',            NULL,                   'Transport',     'EXPENSE', 'Main Checking', 'PIX',           120.00),
    (21, 'Ride share',                 NULL,                   'Transport',     'EXPENSE', 'Credit Card',   'Credit Card',    38.90),
    (8,  'Video streaming',            NULL,                   'Subscriptions', 'EXPENSE', 'Credit Card',   'Credit Card',    39.90),
    (9,  'Music streaming',            NULL,                   'Subscriptions', 'EXPENSE', 'Credit Card',   'Credit Card',    21.90),
    (15, 'Dinner out',                 NULL,                   'Dining',        'EXPENSE', 'Credit Card',   'Credit Card',   142.50),
    (23, 'Lunch with coworkers',       NULL,                   'Dining',        'EXPENSE', 'Credit Card',   'Credit Card',    76.80),
    (27, 'Coffee shop',                NULL,                   'Dining',        'EXPENSE', 'Wallet',        'Cash',           24.00),
    (14, 'Pharmacy',                   NULL,                   'Health',        'EXPENSE', 'Main Checking', 'Debit Card',     63.25),
    (22, 'Cinema',                     NULL,                   'Leisure',       'EXPENSE', 'Credit Card',   'Credit Card',    58.00),
    (25, 'Bookstore',                  'Gift',                 'Leisure',       'EXPENSE', 'Credit Card',   'Credit Card',    94.90)
) AS t(day, description, notes, category, type, account, payment_method, amount)
WHERE (date_trunc('month', current_date) - make_interval(months => m.n) + make_interval(days => t.day - 1))::date <= current_date;

-- Cash withdrawal on the 2nd, monthly move into savings on the 6th and the card statement paid from
-- checking on the 28th (the month's card spend, 472.90): all transfers, never expenses (the card
-- spend above is the expense).
INSERT INTO transfers (id, date, from_account_id, to_account_id, amount, description, created_at, last_modified_at)
SELECT gen_random_uuid(),
       (date_trunc('month', current_date) - make_interval(months => m.n) + make_interval(days => t.day - 1))::date,
       (SELECT id FROM accounts WHERE name = t.from_account),
       (SELECT id FROM accounts WHERE name = t.to_account),
       t.amount, t.description, now(), now()
FROM generate_series(0, 3) AS m(n)
CROSS JOIN (VALUES
    (2,  'Cash withdrawal',       'Main Checking', 'Wallet',       150.00),
    (6,  'Monthly savings',       'Main Checking', 'Savings',     1500.00),
    (28, 'Credit card statement', 'Main Checking', 'Credit Card',  472.90)
) AS t(day, description, from_account, to_account, amount)
WHERE (date_trunc('month', current_date) - make_interval(months => m.n) + make_interval(days => t.day - 1))::date <= current_date;

-- Budgets: one per EXPENSE category, capped from the oldest sample month. Groceries is raised two
-- months ago, so the versioned history (PRD: budgets are versioned, not mutated) has something to show.
INSERT INTO budgets (id, category_id, created_at, last_modified_at)
SELECT gen_random_uuid(), c.id, now(), now()
FROM categories c
WHERE c.name IN ('Groceries', 'Dining', 'Transport', 'Leisure');

INSERT INTO budget_versions (id, budget_id, monthly_cap, effective_from, created_at, last_modified_at)
SELECT gen_random_uuid(), b.id, v.monthly_cap,
       (date_trunc('month', current_date) - make_interval(months => v.months_back))::date,
       now(), now()
FROM (VALUES
    ('Groceries', 3, 600.00),
    ('Groceries', 1, 750.00),
    ('Dining',    3, 300.00),
    ('Transport', 3, 200.00),
    ('Leisure',   3, 150.00)
) AS v(category, months_back, monthly_cap)
JOIN categories c ON c.name = v.category
JOIN budgets b ON b.category_id = c.id;

COMMIT;
