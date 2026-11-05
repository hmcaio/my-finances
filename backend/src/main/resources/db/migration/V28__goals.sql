-- F039: Goals and goal allocations (ADR 0034).
--
-- A goal is something to save toward: a target amount, an optional target date, a planned monthly
-- contribution and three real annual return assumptions (percent). goal_allocations assigns a
-- percentage of an investment product's value to a goal; a product can serve several goals. Edited
-- in place, not versioned.
--
-- Money: target_amount is numeric(19,2) with CHECK > 0. planned_monthly_contribution is the one
-- documented exception, CHECK >= 0, because 0 is a valid plan (let the existing balance grow); see
-- docs/conventions/data-constraints.md. return_* and percentage are not money but percentages, so
-- numeric(5,2), like allocation_plan_entries.target_percentage (F026).
--
-- The rule "a product's percentages across non-archived goals sum to at most 100" spans rows of
-- different goals and ignores archived ones, so a constraint cannot express it: GoalService checks
-- it inside the transaction.
--
-- goal_allocations cascades on goal delete (the allocation is part of the goal). product_id has no
-- cascade: InvestmentProductService refuses a hard delete of an allocated product (409), so the
-- foreign key is a backstop, never reached as a 500.

CREATE TABLE goals (
    id                           uuid PRIMARY KEY,
    name                         varchar(100) NOT NULL,
    notes                        varchar(500),
    target_amount                numeric(19,2) NOT NULL,
    target_date                  date,
    planned_monthly_contribution numeric(19,2) NOT NULL,
    return_pessimistic           numeric(5,2) NOT NULL,
    return_base                  numeric(5,2) NOT NULL,
    return_optimistic            numeric(5,2) NOT NULL,
    status                       varchar(20) NOT NULL,
    show_on_dashboard            boolean NOT NULL DEFAULT true,
    created_at                   timestamptz NOT NULL,
    last_modified_at             timestamptz NOT NULL,
    CONSTRAINT chk_goals_target_positive CHECK (target_amount > 0),
    CONSTRAINT chk_goals_contribution_not_negative CHECK (planned_monthly_contribution >= 0),
    CONSTRAINT chk_goals_return_pessimistic_range CHECK (return_pessimistic BETWEEN -50 AND 100),
    CONSTRAINT chk_goals_return_base_range CHECK (return_base BETWEEN -50 AND 100),
    CONSTRAINT chk_goals_return_optimistic_range CHECK (return_optimistic BETWEEN -50 AND 100),
    CONSTRAINT chk_goals_returns_ordered
        CHECK (return_pessimistic <= return_base AND return_base <= return_optimistic),
    CONSTRAINT chk_goals_status CHECK (status IN ('ACTIVE', 'ACHIEVED', 'ARCHIVED')),
    CONSTRAINT chk_goals_archived_not_on_dashboard
        CHECK (status <> 'ARCHIVED' OR show_on_dashboard = false)
);

CREATE TABLE goal_allocations (
    id                uuid PRIMARY KEY,
    goal_id           uuid NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    product_id        uuid NOT NULL REFERENCES investment_products (id),
    percentage        numeric(5,2) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_goal_allocations_goal_product UNIQUE (goal_id, product_id),
    CONSTRAINT chk_goal_allocations_percentage_range CHECK (percentage > 0 AND percentage <= 100)
);

CREATE INDEX idx_goal_allocations_product_id ON goal_allocations (product_id);
