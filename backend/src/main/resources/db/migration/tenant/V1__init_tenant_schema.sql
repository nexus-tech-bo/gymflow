CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.staff (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES public.accounts(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'RECEPTIONIST')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_${flyway:defaultSchema}_staff_account UNIQUE (account_id)
);

CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.members (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    dni VARCHAR(12) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    birth_date DATE,
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    qr_token UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_${flyway:defaultSchema}_members_dni UNIQUE (dni),
    CONSTRAINT uq_${flyway:defaultSchema}_members_qr_token UNIQUE (qr_token)
);

CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    duration_days INT NOT NULL CHECK (duration_days BETWEEN 1 AND 730),
    price NUMERIC(7, 2) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_${flyway:defaultSchema}_plans_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.cash_sessions (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    opened_at TIMESTAMPTZ NOT NULL,
    opened_by_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.staff(id),
    opening_amount NUMERIC(10, 2) NOT NULL CHECK (opening_amount >= 0),
    closed_at TIMESTAMPTZ,
    closed_by_id BIGINT REFERENCES ${flyway:defaultSchema}.staff(id),
    counted_cash NUMERIC(10, 2) CHECK (counted_cash >= 0),
    notes VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

-- At most one open cash session per gym.
CREATE UNIQUE INDEX IF NOT EXISTS uq_${flyway:defaultSchema}_cash_sessions_single_open
    ON ${flyway:defaultSchema}.cash_sessions (status) WHERE status = 'OPEN';

CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.payments (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.members(id),
    cash_session_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.cash_sessions(id),
    plan_name VARCHAR(60) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    method VARCHAR(10) NOT NULL CHECK (method IN ('CASH', 'YAPE', 'PLIN', 'CARD')),
    reference VARCHAR(40),
    received_by_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.staff(id),
    paid_at TIMESTAMPTZ NOT NULL,
    voided BOOLEAN NOT NULL DEFAULT FALSE,
    void_reason VARCHAR(200),
    voided_at TIMESTAMPTZ,
    voided_by_id BIGINT REFERENCES ${flyway:defaultSchema}.staff(id),
    idempotency_key UUID NOT NULL,
    CONSTRAINT uq_${flyway:defaultSchema}_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_${flyway:defaultSchema}_payments_cash_session ON ${flyway:defaultSchema}.payments (cash_session_id);
CREATE INDEX IF NOT EXISTS idx_${flyway:defaultSchema}_payments_paid_at ON ${flyway:defaultSchema}.payments (paid_at);

CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.memberships (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.members(id),
    plan_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.plans(id),
    payment_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.payments(id),
    plan_name VARCHAR(60) NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    frozen_since DATE,
    frozen_days INT NOT NULL DEFAULT 0 CHECK (frozen_days >= 0),
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_${flyway:defaultSchema}_memberships_payment UNIQUE (payment_id),
    CONSTRAINT ck_${flyway:defaultSchema}_memberships_dates CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_${flyway:defaultSchema}_memberships_member ON ${flyway:defaultSchema}.memberships (member_id);
CREATE INDEX IF NOT EXISTS idx_${flyway:defaultSchema}_memberships_end_date ON ${flyway:defaultSchema}.memberships (end_date);

CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.check_ins (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES ${flyway:defaultSchema}.members(id),
    method VARCHAR(5) NOT NULL CHECK (method IN ('QR', 'DNI')),
    result VARCHAR(10) NOT NULL CHECK (result IN ('ALLOWED', 'DENIED')),
    reason VARCHAR(20),
    checked_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_${flyway:defaultSchema}_check_ins_checked_at ON ${flyway:defaultSchema}.check_ins (checked_at);
CREATE INDEX IF NOT EXISTS idx_${flyway:defaultSchema}_check_ins_member ON ${flyway:defaultSchema}.check_ins (member_id, checked_at);
