-- The base data of the #189 benchmark: five ledger accounts, an INVESTMENT account with 5 products
-- and holdings, and 285 monthly snapshots (57 per holding). Idempotent. Dates are relative to today,
-- so the ledger always ends at the current date.
\set ON_ERROR_STOP on
insert into accounts (id, name, type, opening_balance, opening_balance_date, created_at, last_modified_at, institution_id)
select gen_random_uuid(), n, t,
       case when t = 'INVESTMENT' then null else 1000 end,
       case when t = 'INVESTMENT' then null else current_date - 1900 end,
       now(), now(), (select id from institutions where built_in limit 1)
from (values ('Bench Checking', 'CHECKING'), ('Bench Savings', 'SAVINGS'), ('Bench Cash', 'CASH_WALLET'),
             ('Bench Card A', 'CREDIT_CARD'), ('Bench Card B', 'CREDIT_CARD'), ('Bench Broker', 'INVESTMENT')) v(n, t)
where not exists (select 1 from accounts where name = 'Bench Checking');

insert into investment_products (id, investment_category_id, name, created_at, last_modified_at)
select gen_random_uuid(), (select id from investment_categories order by name limit 1), 'Bench Product ' || g, now(), now()
from generate_series(1, 5) g
where not exists (select 1 from investment_products where name like 'Bench Product %');

insert into investment_holdings (id, product_id, account_id, created_at, last_modified_at)
select gen_random_uuid(), p.id, (select id from accounts where name = 'Bench Broker'), now(), now()
from investment_products p
where p.name like 'Bench Product %'
  and not exists (select 1 from investment_holdings where product_id = p.id);

insert into investment_snapshots (id, date, balance, created_at, last_modified_at, holding_id)
select gen_random_uuid(), current_date - 30 * g, 1000 + g * 10 + (g % 7), now(), now(), h.id
from investment_holdings h, generate_series(0, 56) g
where h.account_id = (select id from accounts where name = 'Bench Broker')
  and not exists (select 1 from investment_snapshots where holding_id = h.id);
