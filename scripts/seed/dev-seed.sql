-- Optional sample data for the dev stack (run it with scripts/seed-dev.sh; docs/conventions/tooling.md).
-- NOT a Flyway migration: nothing applies it automatically, and it is not packaged into any image.
--
-- Covers: one institution, categories, five accounts (checking, savings, cash wallet, credit card
-- and an INVESTMENT "Brokerage"), ~4 months of transactions, credit-card/savings/cash transfers,
-- budgets (with one cap change), four investment products and holdings (a Treasury bond with an
-- opening position, a CDB, two FIIs) with trade confirmations, snapshots and dividends, a fifth
-- FII that was bought, paid a dividend, then fully sold and closed, a two-version FII allocation
-- plan, one vehicle with fuel fill-ups, and four recurring templates (one with two versions) whose
-- past cycles are already approved. The current cycle is left for the app's catch-up, so the pending
-- list is not empty.
--
-- Every date is relative to current_date (pg_temp.d below), so the data always looks recent.
-- Nothing is random: re-seeding an emptied database gives the same shape. The ids come from
-- gen_random_uuid(), like the migrations' own seeds. Reference rows the migrations already create
-- (payment methods, the built-in/fuel/dividend categories, the investment taxonomy and segments)
-- are reused by name, never duplicated. Investment events are dated in past months only, so none
-- can fall after "today".
--
-- Refuses to run unless accounts, transactions, transfers, investment products, vehicles and
-- recurring templates are all empty, so it can never mix sample rows into data you entered. One
-- transaction: it either seeds everything or nothing.

BEGIN;

-- d(n, day): `day` (clamped to 1..28 by the caller) of the month n months before the current one.
CREATE FUNCTION pg_temp.d(n int, day int) RETURNS date LANGUAGE sql STABLE AS $$
    SELECT (date_trunc('month', current_date) - make_interval(months => n) + make_interval(days => day - 1))::date
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM accounts) OR EXISTS (SELECT 1 FROM transactions)
       OR EXISTS (SELECT 1 FROM transfers) OR EXISTS (SELECT 1 FROM investment_products)
       OR EXISTS (SELECT 1 FROM vehicles) OR EXISTS (SELECT 1 FROM recurring_templates) THEN
        RAISE EXCEPTION 'dev seed refused: accounts, transactions, transfers, investment products, vehicles or recurring templates already hold data (empty the database first)';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------------------------

INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)
VALUES (gen_random_uuid(), 'Sample Bank', false, now(), now())
ON CONFLICT (name) DO NOTHING;

-- "Fuel" and "Dividends" already exist (V18, V20) and are the flagged fuel/dividend categories.
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

INSERT INTO vehicles (id, name, created_at, last_modified_at)
VALUES (gen_random_uuid(), 'Family Car', now(), now());

-- ---------------------------------------------------------------------------------------------
-- Accounts. Opening balances sit on the first day of the month three months back, just before the
-- oldest sample transaction. The INVESTMENT account has no opening balance (V13 CHECK): its balance
-- is the sum of its holdings' latest snapshots.
-- ---------------------------------------------------------------------------------------------

INSERT INTO accounts (id, name, type, opening_balance, opening_balance_date, institution_id, created_at, last_modified_at)
SELECT gen_random_uuid(), a.name, a.type, a.opening_balance, pg_temp.d(3, 1),
       (SELECT id FROM institutions WHERE name = 'Sample Bank'), now(), now()
FROM (VALUES
    ('Main Checking', 'CHECKING',    6000.00),
    ('Savings',       'SAVINGS',     8000.00),
    ('Wallet',        'CASH_WALLET',  150.00),
    ('Credit Card',   'CREDIT_CARD',    0.00)
) AS a(name, type, opening_balance);

INSERT INTO accounts (id, name, type, opening_balance, opening_balance_date, institution_id, created_at, last_modified_at)
VALUES (gen_random_uuid(), 'Brokerage', 'INVESTMENT', NULL, NULL,
        (SELECT id FROM institutions WHERE name = 'Sample Bank'), now(), now());

-- ---------------------------------------------------------------------------------------------
-- Recurring templates. The versions start at the oldest sample month; "Video streaming" gets a
-- second, dearer version from last month. last_generated_for is last month: every cycle up to then
-- is already a transaction (below), and this month's is left for the app's catch-up to turn into a
-- pending occurrence once its day arrives.
-- ---------------------------------------------------------------------------------------------

INSERT INTO recurring_templates (id, category_id, account_id, description, active, last_generated_for, created_at, last_modified_at)
SELECT gen_random_uuid(),
       (SELECT id FROM categories WHERE name = t.category),
       (SELECT id FROM accounts WHERE name = 'Main Checking'),
       t.description, true, pg_temp.d(1, 1), now(), now()
FROM (VALUES
    ('Monthly salary',  'Salary'),
    ('Apartment rent',  'Rent'),
    ('Internet',        'Utilities'),
    ('Video streaming', 'Subscriptions')
) AS t(description, category);

INSERT INTO recurring_template_versions (id, template_id, amount, day_of_month, effective_from, created_at, last_modified_at)
SELECT gen_random_uuid(), rt.id, v.amount, v.day_of_month, pg_temp.d(v.months_back, 1), now(), now()
FROM (VALUES
    ('Monthly salary',  6200.00,  5, 3),
    ('Apartment rent',  1800.00, 10, 3),
    ('Internet',         119.90, 13, 3),
    ('Video streaming',   39.90,  8, 3),
    ('Video streaming',   44.90,  8, 1)
) AS v(description, amount, day_of_month, months_back)
JOIN recurring_templates rt ON rt.description = v.description;

-- ---------------------------------------------------------------------------------------------
-- Transactions. One row per month-offset (0 = this month .. 3 = three months back) x line below;
-- lines dated after today are dropped. A `recurring` line is the template of that description: its
-- amount and link come from the version effective in that month, and only past months are seeded.
-- ---------------------------------------------------------------------------------------------

INSERT INTO transactions (id, date, amount, category_id, type, account_id, payment_method_id,
                          description, additional_notes, recurring_template_version_id,
                          created_at, last_modified_at)
SELECT gen_random_uuid(),
       pg_temp.d(m.n, t.day),
       COALESCE(rv.amount, t.amount),
       (SELECT id FROM categories WHERE name = t.category),
       t.type,
       (SELECT id FROM accounts WHERE name = t.account),
       (SELECT id FROM payment_methods WHERE name = t.payment_method),
       t.description, t.notes, rv.id, now(), now()
FROM generate_series(0, 3) AS m(n)
CROSS JOIN (VALUES
    -- day, description,           notes,                category,        type,      account,         payment method, amount, recurring
    (5,  'Monthly salary',         NULL,                  'Salary',        'INCOME',  'Main Checking', 'PIX',          NULL::numeric, true),
    (10, 'Apartment rent',         NULL,                  'Rent',          'EXPENSE', 'Main Checking', 'PIX',          NULL,          true),
    (13, 'Internet',               NULL,                  'Utilities',     'EXPENSE', 'Main Checking', 'PIX',          NULL,          true),
    (8,  'Video streaming',        NULL,                  'Subscriptions', 'EXPENSE', 'Credit Card',   'Credit Card',  NULL,          true),
    (18, 'Freelance project',      'Invoice paid by PIX', 'Freelance',     'INCOME',  'Main Checking', 'PIX',           450.00,       false),
    (12, 'Electricity bill',       NULL,                  'Utilities',     'EXPENSE', 'Main Checking', 'PIX',           185.40,       false),
    (3,  'Supermarket',            NULL,                  'Groceries',     'EXPENSE', 'Main Checking', 'Debit Card',    212.35,       false),
    (11, 'Supermarket',            NULL,                  'Groceries',     'EXPENSE', 'Main Checking', 'Debit Card',    187.60,       false),
    (19, 'Supermarket',            NULL,                  'Groceries',     'EXPENSE', 'Main Checking', 'Debit Card',    234.10,       false),
    (26, 'Farmers market',         NULL,                  'Groceries',     'EXPENSE', 'Wallet',        'Cash',           68.00,       false),
    (7,  'Bus pass top-up',        NULL,                  'Transport',     'EXPENSE', 'Main Checking', 'PIX',           120.00,       false),
    (21, 'Ride share',             NULL,                  'Transport',     'EXPENSE', 'Credit Card',   'Credit Card',    38.90,       false),
    (9,  'Music streaming',        NULL,                  'Subscriptions', 'EXPENSE', 'Credit Card',   'Credit Card',    21.90,       false),
    (15, 'Dinner out',             NULL,                  'Dining',        'EXPENSE', 'Credit Card',   'Credit Card',   142.50,       false),
    (23, 'Lunch with coworkers',   NULL,                  'Dining',        'EXPENSE', 'Credit Card',   'Credit Card',    76.80,       false),
    (27, 'Coffee shop',            NULL,                  'Dining',        'EXPENSE', 'Wallet',        'Cash',           24.00,       false),
    (14, 'Pharmacy',               NULL,                  'Health',        'EXPENSE', 'Main Checking', 'Debit Card',     63.25,       false),
    (22, 'Cinema',                 NULL,                  'Leisure',       'EXPENSE', 'Credit Card',   'Credit Card',    58.00,       false),
    (25, 'Bookstore',              'Gift',                'Leisure',       'EXPENSE', 'Credit Card',   'Credit Card',    94.90,       false)
) AS t(day, description, notes, category, type, account, payment_method, amount, recurring)
LEFT JOIN recurring_templates rt ON t.recurring AND rt.description = t.description
LEFT JOIN LATERAL (
    SELECT v.id, v.amount FROM recurring_template_versions v
    WHERE v.template_id = rt.id AND v.effective_from <= pg_temp.d(m.n, 1)
    ORDER BY v.effective_from DESC LIMIT 1
) rv ON true
WHERE (NOT t.recurring OR m.n >= 1)
  AND pg_temp.d(m.n, t.day) <= current_date;

-- Fuel fill-ups: the "Fuel" category takes the vehicle details (all-or-nothing, V18 CHECKs); the
-- amount is liters x price per liter. The odometer grows by each fill's km since the last one.
INSERT INTO transactions (id, date, amount, category_id, type, account_id, payment_method_id,
                          description, vehicle_id, fuel_type, liters, price_per_liter,
                          km_since_last_fill, odometer, created_at, last_modified_at)
SELECT gen_random_uuid(),
       pg_temp.d(f.n, f.day),
       round(f.liters * f.price_per_liter, 2),
       (SELECT id FROM categories WHERE fuel_category),
       'EXPENSE',
       (SELECT id FROM accounts WHERE name = 'Main Checking'),
       (SELECT id FROM payment_methods WHERE name = 'Debit Card'),
       'Fuel', (SELECT id FROM vehicles WHERE name = 'Family Car'),
       f.fuel_type, f.liters, f.price_per_liter, f.km_since_last_fill, f.odometer, now(), now()
FROM (VALUES
    (3, 6, 'GASOLINA',          38.200, 6.199, 412.0, 41200.0),
    (2, 4, 'GASOLINA',          36.500, 6.259, 389.5, 41589.5),
    (1, 4, 'ETANOL',            42.100, 4.399, 401.2, 41990.7),
    (0, 3, 'GASOLINA_ADITIVADA', 35.000, 6.499, 372.8, 42363.5)
) AS f(n, day, fuel_type, liters, price_per_liter, km_since_last_fill, odometer)
WHERE pg_temp.d(f.n, f.day) <= current_date;

-- ---------------------------------------------------------------------------------------------
-- Investments. Taxonomy rows (categories, subcategories, FII segments) come from V13/V20/V24/V26.
-- The products are fictitious (DEMO tickers), one holding each in the Brokerage account.
-- ---------------------------------------------------------------------------------------------

INSERT INTO investment_products (id, investment_category_id, investment_subcategory_id, name, ticker, segment_id,
                                 additional_notes, created_at, last_modified_at)
SELECT gen_random_uuid(), ic.id, isc.id, p.name, p.ticker, seg.id, p.notes, now(), now()
FROM (VALUES
    ('Tesouro Selic 2029',     NULL,     'Fixed Income',    'Tesouro Selic',  NULL,                  'Existing position, see its opening position'),
    ('CDB 110% CDI',           NULL,     'Fixed Income',    'CDB',            NULL,                  NULL),
    ('Demo Logistics FII',     'DEMO11', 'Variable Income', 'REITs (FIIs)',   'Logística',           NULL),
    ('Demo Receivables FII',   'DEMO12', 'Variable Income', 'REITs (FIIs)',   'Papel (Recebíveis)',  NULL),
    ('Demo Hotel FII',         'DEMO13', 'Variable Income', 'REITs (FIIs)',   'Hotel',               'Sold out and closed, kept for its history')
) AS p(name, ticker, category, subcategory, segment, notes)
JOIN investment_categories ic ON ic.name = p.category
JOIN investment_subcategories isc ON isc.investment_category_id = ic.id AND isc.name = p.subcategory
LEFT JOIN investment_segments seg ON seg.name = p.segment;

INSERT INTO investment_holdings (id, product_id, account_id, created_at, last_modified_at)
SELECT gen_random_uuid(), ip.id, (SELECT id FROM accounts WHERE name = 'Brokerage'), now(), now()
FROM investment_products ip;

-- F038: a starting position that moves no cash. Trades on that holding must not be dated before it.
INSERT INTO investment_opening_positions (id, holding_id, as_of_date, quantity, unit_price, created_at, last_modified_at)
SELECT gen_random_uuid(), h.id, pg_temp.d(3, 1), 0.50000000, 15000.00000000, now(), now()
FROM investment_holdings h
JOIN investment_products ip ON ip.id = h.product_id
WHERE ip.name = 'Tesouro Selic 2029';

-- Trade confirmations: each is one transfer between Main Checking and the Brokerage account with a
-- single product line. The transfer's amount is |sum(BUY total - SELL total) + taxes| rounded to 2
-- places, and its direction follows the sign (a net cost goes checking -> brokerage, net proceeds
-- the other way): the same rule as Transfer.createTradeConfirmation. A line's resulting balance
-- is also the holding's snapshot on that day, exactly as TransferService records it.
CREATE TEMP TABLE seed_trade (
    transfer_id       uuid NOT NULL DEFAULT gen_random_uuid(),
    n                 int  NOT NULL,
    day               int  NOT NULL,
    description       text NOT NULL,
    taxes             numeric(19,2) NOT NULL,
    product           text NOT NULL,
    side              text NOT NULL,
    quantity          numeric(19,8) NOT NULL,
    unit_price        numeric(19,8) NOT NULL,
    resulting_balance numeric(19,2) NOT NULL,
    close_holding     boolean NOT NULL DEFAULT false
) ON COMMIT DROP;

INSERT INTO seed_trade (n, day, description, taxes, product, side, quantity, unit_price, resulting_balance) VALUES
    (3,  8, 'Bought DEMO11',           0.30, 'Demo Logistics FII',   'BUY',  10,  98.50,    985.00),
    (3,  9, 'CDB deposit',             0.00, 'CDB 110% CDI',         'BUY',   1, 1500.00,  1500.00),
    (2,  9, 'CDB deposit',             0.00, 'CDB 110% CDI',         'BUY',   1, 1500.00,  3015.20),
    (2, 14, 'Bought DEMO12',           0.28, 'Demo Receivables FII','BUY',   8, 102.30,    818.40),
    (1,  4, 'Treasury top-up',         0.00, 'Tesouro Selic 2029',   'BUY',   0.10, 15210.00, 9129.00),
    (1, 16, 'Bought DEMO11',           0.20, 'Demo Logistics FII',   'BUY',   5, 101.20,   1518.00),
    (1, 20, 'Partial CDB redemption',  0.00, 'CDB 110% CDI',         'SELL',  1, 1520.00,  1513.40);

-- The closed holding: bought, then sold in full. The sell line carries a zero resulting balance
-- (the holding's last snapshot) and close_holding, the combination TransferService accepts for a
-- close (a holding can only be closed while its latest snapshot is zero).
INSERT INTO seed_trade (n, day, description, taxes, product, side, quantity, unit_price, resulting_balance, close_holding) VALUES
    (3, 12, 'Bought DEMO13',           0.20, 'Demo Hotel FII',       'BUY',   6,   85.00,    510.00, false),
    (1, 12, 'Sold DEMO13 in full',     0.15, 'Demo Hotel FII',       'SELL',  6,   88.40,      0.00, true);

INSERT INTO transfers (id, date, from_account_id, to_account_id, amount, description, taxes, created_at, last_modified_at)
SELECT s.transfer_id, pg_temp.d(s.n, s.day),
       CASE WHEN s.net > 0 THEN (SELECT id FROM accounts WHERE name = 'Main Checking') ELSE (SELECT id FROM accounts WHERE name = 'Brokerage') END,
       CASE WHEN s.net > 0 THEN (SELECT id FROM accounts WHERE name = 'Brokerage') ELSE (SELECT id FROM accounts WHERE name = 'Main Checking') END,
       abs(s.net), s.description, s.taxes, now(), now()
FROM (SELECT t.*, round((CASE t.side WHEN 'BUY' THEN 1 ELSE -1 END) * t.quantity * t.unit_price + t.taxes, 2) AS net
      FROM seed_trade t) s;

INSERT INTO transfer_trade_lines (id, transfer_id, product_id, side, quantity, unit_price, resulting_balance,
                                  close_holding, created_at, last_modified_at)
SELECT gen_random_uuid(), t.transfer_id, ip.id, t.side, t.quantity, t.unit_price, t.resulting_balance,
       t.close_holding, now(), now()
FROM seed_trade t
JOIN investment_products ip ON ip.name = t.product;

-- Closing sets the holding's closed date (here the day of the closing trade).
UPDATE investment_holdings h
SET closed_date = pg_temp.d(t.n, t.day)
FROM seed_trade t
JOIN investment_products ip ON ip.name = t.product
WHERE t.close_holding AND h.product_id = ip.id;

INSERT INTO investment_snapshots (id, date, balance, holding_id, created_at, last_modified_at)
SELECT gen_random_uuid(), pg_temp.d(t.n, t.day), t.resulting_balance, h.id, now(), now()
FROM seed_trade t
JOIN investment_products ip ON ip.name = t.product
JOIN investment_holdings h ON h.product_id = ip.id;

-- Month-start valuations between the trades (the first of the month, never a trade day). None after
-- a holding's close: a closed holding takes no more snapshots.
INSERT INTO investment_snapshots (id, date, balance, holding_id, created_at, last_modified_at)
SELECT gen_random_uuid(), pg_temp.d(k.n, 1), k.balance, h.id, now(), now()
FROM (VALUES
    ('Tesouro Selic 2029',   3, 7500.00),
    ('Tesouro Selic 2029',   2, 7562.10),
    ('Tesouro Selic 2029',   1, 7620.45),
    ('Tesouro Selic 2029',   0, 9171.30),
    ('CDB 110% CDI',         2, 1506.90),
    ('CDB 110% CDI',         1, 3028.10),
    ('CDB 110% CDI',         0, 1520.70),
    ('Demo Logistics FII',   2, 1002.00),
    ('Demo Logistics FII',   1,  980.00),
    ('Demo Logistics FII',   0, 1531.50),
    ('Demo Receivables FII', 1,  829.60),
    ('Demo Receivables FII', 0,  834.40),
    ('Demo Hotel FII',       2,  512.40),
    ('Demo Hotel FII',       1,  505.80)
) AS k(product, n, balance)
JOIN investment_products ip ON ip.name = k.product
JOIN investment_holdings h ON h.product_id = ip.id;

-- FII allocation plan (ADR 0023): versioned like a budget, FII products only, each version's
-- targets summing to exactly 100. The closed FII is not in it. Targets are rebalanced from last month.
INSERT INTO allocation_plans (id, created_at, last_modified_at) VALUES (gen_random_uuid(), now(), now());

INSERT INTO allocation_plan_versions (id, plan_id, effective_from, created_at, last_modified_at)
SELECT gen_random_uuid(), (SELECT id FROM allocation_plans), pg_temp.d(v.months_back, 1), now(), now()
FROM (VALUES (3), (1)) AS v(months_back);

INSERT INTO allocation_plan_entries (id, version_id, investment_product_id, target_percentage, created_at, last_modified_at)
SELECT gen_random_uuid(), apv.id, ip.id, e.target_percentage, now(), now()
FROM (VALUES
    (3, 'Demo Logistics FII',   50.00),
    (3, 'Demo Receivables FII', 50.00),
    (1, 'Demo Logistics FII',   60.00),
    (1, 'Demo Receivables FII', 40.00)
) AS e(months_back, product, target_percentage)
JOIN allocation_plan_versions apv ON apv.effective_from = pg_temp.d(e.months_back, 1)
JOIN investment_products ip ON ip.name = e.product;

-- Dividends: income in the flagged dividend category, tied to the holding that paid it and received
-- in a normal (non-INVESTMENT) account.
INSERT INTO transactions (id, date, amount, category_id, type, account_id, payment_method_id,
                          description, investment_holding_id, created_at, last_modified_at)
SELECT gen_random_uuid(), pg_temp.d(v.n, v.day), v.amount,
       (SELECT id FROM categories WHERE dividend_category),
       'INCOME',
       (SELECT id FROM accounts WHERE name = 'Main Checking'),
       (SELECT id FROM payment_methods WHERE name = 'PIX'),
       v.description, h.id, now(), now()
FROM (VALUES
    ('Demo Logistics FII',   2, 15,  9.20, 'DEMO11 dividend'),
    ('Demo Logistics FII',   1, 15, 14.25, 'DEMO11 dividend'),
    ('Demo Receivables FII', 1, 15,  8.40, 'DEMO12 dividend'),
    ('Demo Hotel FII',       2, 15,  4.80, 'DEMO13 dividend')
) AS v(product, n, day, amount, description)
JOIN investment_products ip ON ip.name = v.product
JOIN investment_holdings h ON h.product_id = ip.id;

-- ---------------------------------------------------------------------------------------------
-- Plain transfers. Cash withdrawal on the 2nd, monthly move into savings on the 6th and the card
-- statement paid from checking on the 28th (that month's card spend, so the card nets to zero):
-- all transfers, never expenses (the card spend above is the expense).
-- ---------------------------------------------------------------------------------------------

INSERT INTO transfers (id, date, from_account_id, to_account_id, amount, description, created_at, last_modified_at)
SELECT gen_random_uuid(), pg_temp.d(m.n, t.day),
       (SELECT id FROM accounts WHERE name = t.from_account),
       (SELECT id FROM accounts WHERE name = t.to_account),
       t.amount, t.description, now(), now()
FROM generate_series(0, 3) AS m(n)
CROSS JOIN (VALUES
    (2,  'Cash withdrawal',       'Main Checking', 'Wallet',       150.00),
    (6,  'Monthly savings',       'Main Checking', 'Savings',     1500.00)
) AS t(day, description, from_account, to_account, amount)
WHERE pg_temp.d(m.n, t.day) <= current_date;

INSERT INTO transfers (id, date, from_account_id, to_account_id, amount, description, created_at, last_modified_at)
SELECT gen_random_uuid(), pg_temp.d(m.n, 28),
       (SELECT id FROM accounts WHERE name = 'Main Checking'), card.id, spend.total,
       'Credit card statement', now(), now()
FROM generate_series(0, 3) AS m(n)
CROSS JOIN (SELECT id FROM accounts WHERE name = 'Credit Card') card
CROSS JOIN LATERAL (
    SELECT sum(t.amount) AS total FROM transactions t
    WHERE t.account_id = card.id AND t.date >= pg_temp.d(m.n, 1) AND t.date < pg_temp.d(m.n - 1, 1)
) spend
WHERE spend.total > 0 AND pg_temp.d(m.n, 28) <= current_date;

-- ---------------------------------------------------------------------------------------------
-- Budgets: one per EXPENSE category, capped from the oldest sample month. Groceries is raised two
-- months ago, so the versioned history (PRD: budgets are versioned, not mutated) has something to show.
-- ---------------------------------------------------------------------------------------------

INSERT INTO budgets (id, category_id, created_at, last_modified_at)
SELECT gen_random_uuid(), c.id, now(), now()
FROM categories c
WHERE c.name IN ('Groceries', 'Dining', 'Transport', 'Leisure');

INSERT INTO budget_versions (id, budget_id, monthly_cap, effective_from, created_at, last_modified_at)
SELECT gen_random_uuid(), b.id, v.monthly_cap, pg_temp.d(v.months_back, 1), now(), now()
FROM (VALUES
    ('Groceries', 3, 600.00),
    ('Groceries', 1, 750.00),
    ('Dining',    3, 300.00),
    ('Transport', 3, 200.00),
    ('Leisure',   3, 150.00)
) AS v(category, months_back, monthly_cap)
JOIN categories c ON c.name = v.category
JOIN budgets b ON b.category_id = c.id;

DROP FUNCTION pg_temp.d(int, int);

COMMIT;
