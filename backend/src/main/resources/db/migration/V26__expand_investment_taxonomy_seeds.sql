-- Issue #227 (ADR 0028 addendum): extend the fixed, migration-seeded investment taxonomies.
-- V13 (sub-categories) and V24 (segments) are applied in tagged releases and stay untouched; this
-- only adds rows. ON CONFLICT makes it safe to re-run against a database that already has any of
-- them, and no existing name is renamed (code looks several up by name, e.g. 'REITs (FIIs)').

-- FII sectors seen in Fundamentus, Investidor10, Status Invest and ANBIMA but missing from V24.
INSERT INTO investment_segments (id, name, created_at, last_modified_at) VALUES
    (gen_random_uuid(), 'Fundo de Fundos',      now(), now()),
    (gen_random_uuid(), 'Hotel',                now(), now()),
    (gen_random_uuid(), 'Agências Bancárias',   now(), now()),
    (gen_random_uuid(), 'Educacional',          now(), now()),
    (gen_random_uuid(), 'Desenvolvimento',      now(), now())
ON CONFLICT (name) DO NOTHING;

-- Same shape as V13's sub-category seed: joined on the category name; unique per category.
INSERT INTO investment_subcategories (id, investment_category_id, name, created_at, last_modified_at)
SELECT gen_random_uuid(), category.id, seed.name, now(), now()
FROM (VALUES
    ('Fixed Income',  'RDB'),
    ('Fixed Income',  'Tesouro RendA+'),
    ('Fixed Income',  'Tesouro Educa+'),
    ('Funds',         'Renda Fixa DI'),
    ('Funds',         'Crédito Privado'),
    ('Funds',         'FIDC'),
    ('Funds',         'FIP'),
    ('Funds',         'Cambial'),
    ('International', 'REITs'),
    ('International', 'Funds'),
    ('Crypto',        'Bitcoin'),
    ('Crypto',        'Stablecoins'),
    ('Crypto',        'Altcoins'),
    ('Other',         'Gold (Ouro)')
) AS seed (category_name, name)
JOIN investment_categories category ON category.name = seed.category_name
ON CONFLICT (investment_category_id, name) DO NOTHING;
