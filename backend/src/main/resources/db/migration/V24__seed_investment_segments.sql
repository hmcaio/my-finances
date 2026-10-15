-- F034 (ADR 0028): seed `investment_segments` for the first time. V20 created the table with no
-- rows - this closes that gap as a side effect of locking the taxonomy down (every row these four
-- entities will ever have is now migration-seeded, never user-created). A common B3 FII sector
-- list, confirmed with the user rather than invented silently (ADR 0028's own Decisions section).
--
-- Plain INSERTs, no new column: the table already has id, name (UNIQUE, V20) and audit columns,
-- and nothing will ever write to it through the app again (InvestmentSegmentService/Controller
-- lost their create/rename/delete in this same feature).

INSERT INTO investment_segments (id, name, created_at, last_modified_at) VALUES
    (gen_random_uuid(), 'Lajes Corporativas',   now(), now()),
    (gen_random_uuid(), 'Shoppings',            now(), now()),
    (gen_random_uuid(), 'Logística',            now(), now()),
    (gen_random_uuid(), 'Papel (Recebíveis)',   now(), now()),
    (gen_random_uuid(), 'Híbrido',              now(), now()),
    (gen_random_uuid(), 'Fiagro',               now(), now()),
    (gen_random_uuid(), 'Hospitalar',           now(), now()),
    (gen_random_uuid(), 'Residencial',          now(), now()),
    (gen_random_uuid(), 'Outros',               now(), now());
