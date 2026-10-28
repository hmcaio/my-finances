-- Replaces the ledger with :n rows (psql -v n=5000): 80% transactions (25% income), 20% transfers
-- (35% credit-card payments, 2% buy trades with a trade line, the rest between asset accounts),
-- accented descriptions, 30% with notes, dates spread over the last ~5 years. Deterministic
-- (setseed) up to the dates moving with the current date. No audit_log rows: no benchmarked read
-- touches them.
\set ON_ERROR_STOP on
select setseed(0.189);
delete from transfer_trade_lines;
delete from transfers;
delete from transactions;
create temp table acc as select id, type from accounts where type <> 'INVESTMENT';

select :n * 80 / 100 as tx, :n * 20 / 100 as tf \gset
select :tf * 35 / 100 as cc, :tf * 2 / 100 as trade \gset
select :tf - :cc - :trade as plain \gset

insert into transactions (id, date, amount, category_id, type, account_id, payment_method_id, description, additional_notes, created_at, last_modified_at)
select gen_random_uuid(), current_date - (random() * 1880)::int, round((5 + random() * 495)::numeric, 2), c.id, c.type, a.id, p.id,
       (array['Padaria Açaí', 'Café da manhã', 'Salário', 'Supermercado São João', 'Conta de luz', 'Farmácia', 'Restaurante Ação', 'Combustível'])[1 + (random() * 7)::int] || ' ' || g,
       case when random() < 0.3 then 'Observação com acentuação ã é ç' end, now(), now()
from generate_series(1, :tx) g
cross join lateral (select id from acc where g is not null order by random() limit 1) a
cross join lateral (select id, type from categories
                     where type = (case when g % 4 = 0 then 'INCOME' else 'EXPENSE' end)
                       and not fuel_category and not dividend_category and g is not null
                     order by random() limit 1) c
cross join lateral (select id from payment_methods where g is not null order by random() limit 1) p;

insert into transfers (id, date, from_account_id, to_account_id, amount, description, additional_notes, created_at, last_modified_at)
select gen_random_uuid(), current_date - (random() * 1880)::int, x.f, x.t, round((10 + random() * 990)::numeric, 2),
       'Transferência ' || g, case when random() < 0.3 then 'Nota ã é ç' end, now(), now()
from generate_series(1, :plain) g
cross join lateral (select a1.id f, a2.id t from acc a1 join acc a2 on a1.id <> a2.id
                     where a1.type <> 'CREDIT_CARD' and a2.type <> 'CREDIT_CARD' and g is not null
                     order by random() limit 1) x;

insert into transfers (id, date, from_account_id, to_account_id, amount, description, additional_notes, created_at, last_modified_at)
select gen_random_uuid(), current_date - (random() * 1880)::int, (select id from accounts where name = 'Bench Checking'), x.t,
       round((50 + random() * 950)::numeric, 2), 'Pagamento fatura ' || g, null, now(), now()
from generate_series(1, :cc) g
cross join lateral (select id t from acc where type = 'CREDIT_CARD' and g is not null order by random() limit 1) x;

-- A transfer with a trade confirmation must carry taxes (the domain rejects it otherwise), so the
-- reports that load every transfer would fail on a trade row without it.
with t as (
  insert into transfers (id, date, from_account_id, to_account_id, amount, description, additional_notes, created_at, last_modified_at, taxes)
  select gen_random_uuid(), current_date - (random() * 1880)::int, (select id from accounts where name = 'Bench Checking'),
         (select id from accounts where name = 'Bench Broker'), 500, 'Compra ' || g, null, now(), now(), 0
  from generate_series(1, :trade) g
  returning id)
insert into transfer_trade_lines (id, transfer_id, product_id, side, quantity, unit_price, resulting_balance, close_holding, created_at, last_modified_at)
select gen_random_uuid(), t.id, (select id from investment_products where name like 'Bench Product %' order by random() limit 1),
       'BUY', 10, 50, null, false, now(), now()
from t;

analyze;
select (select count(*) from transactions) as transactions, (select count(*) from transfers) as transfers,
       (select count(*) from transfer_trade_lines) as trade_lines;
