-- AML Alert Triage baseline schema
-- Note: transaction entity maps to table "txn" to avoid PostgreSQL reserved word "transaction".
-- Note: case entity maps to table "case_record" because "case" is reserved.

CREATE TABLE customer (
    id                      BIGSERIAL PRIMARY KEY,
    customer_ref            VARCHAR(64)  NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    segment                 VARCHAR(32)  NOT NULL,
    legal_form              VARCHAR(32),
    industry                VARCHAR(128),
    incorporation_country   VARCHAR(2)      NOT NULL,
    registration_date       DATE,
    crr                     VARCHAR(16)  NOT NULL,
    monitoring_status       VARCHAR(32)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_ref UNIQUE (customer_ref)
);

CREATE TABLE txn (
    id                      BIGSERIAL PRIMARY KEY,
    txn_ref                 VARCHAR(64)    NOT NULL,
    customer_id             BIGINT         NOT NULL REFERENCES customer (id),
    account_ref             VARCHAR(64)    NOT NULL,
    direction               VARCHAR(16)    NOT NULL,
    amount                  NUMERIC(18, 2) NOT NULL,
    currency                VARCHAR(3)        NOT NULL,
    amount_gbp              NUMERIC(18, 2) NOT NULL,
    fx_rate_used            NUMERIC(18, 8),
    fx_rate_date            DATE,
    counterparty_name       VARCHAR(255),
    counterparty_ref        VARCHAR(64),
    counterparty_country    VARCHAR(2),
    executed_at             TIMESTAMPTZ    NOT NULL,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_txn_ref UNIQUE (txn_ref)
);

CREATE INDEX idx_txn_customer_id ON txn (customer_id);
CREATE INDEX idx_txn_customer_executed_at ON txn (customer_id, executed_at);
CREATE INDEX idx_txn_account_ref ON txn (account_ref);
CREATE INDEX idx_txn_counterparty_ref ON txn (counterparty_ref);

CREATE TABLE case_record (
    id                      BIGSERIAL PRIMARY KEY,
    case_ref                VARCHAR(64)  NOT NULL,
    customer_id             BIGINT       NOT NULL REFERENCES customer (id),
    priority_score          INT          NOT NULL,
    priority_band           VARCHAR(16)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    crr_review_required     BOOLEAN      NOT NULL DEFAULT FALSE,
    prior_recent_cases      INT          NOT NULL DEFAULT 0,
    assigned_to             VARCHAR(128),
    sla_due_at              TIMESTAMPTZ,
    window_start            TIMESTAMPTZ,
    window_end              TIMESTAMPTZ,
    disposition_reason      TEXT,
    disposed_by             VARCHAR(128),
    disposed_at             TIMESTAMPTZ,
    opened_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_case_ref UNIQUE (case_ref)
);

CREATE INDEX idx_case_customer_id ON case_record (customer_id);
CREATE INDEX idx_case_status ON case_record (status);
CREATE INDEX idx_case_priority_band ON case_record (priority_band);
CREATE INDEX idx_case_assigned_to ON case_record (assigned_to);
CREATE INDEX idx_case_sla_due_at ON case_record (sla_due_at);
CREATE INDEX idx_case_opened_at ON case_record (opened_at);

CREATE TABLE alert (
    id                      BIGSERIAL PRIMARY KEY,
    customer_id             BIGINT         NOT NULL REFERENCES customer (id),
    case_id                 BIGINT         REFERENCES case_record (id),
    rule_code               VARCHAR(16)    NOT NULL,
    rule_name               VARCHAR(128)   NOT NULL,
    strength                NUMERIC(4, 3)  NOT NULL,
    points                  INT            NOT NULL,
    rule_params_snapshot    JSONB          NOT NULL DEFAULT '{}'::jsonb,
    evidence_snapshot       JSONB          NOT NULL DEFAULT '{}'::jsonb,
    window_start            TIMESTAMPTZ,
    window_end              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_customer_id ON alert (customer_id);
CREATE INDEX idx_alert_case_id ON alert (case_id);
CREATE INDEX idx_alert_rule_code ON alert (rule_code);
CREATE INDEX idx_alert_created_at ON alert (created_at);

CREATE TABLE audit_event (
    id                      BIGSERIAL PRIMARY KEY,
    actor                   VARCHAR(128) NOT NULL,
    occurred_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    action                  VARCHAR(64)  NOT NULL,
    entity_type             VARCHAR(64)  NOT NULL,
    entity_id               BIGINT       NOT NULL,
    details_json            JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_entity ON audit_event (entity_type, entity_id);
CREATE INDEX idx_audit_occurred_at ON audit_event (occurred_at);
CREATE INDEX idx_audit_action ON audit_event (action);

CREATE TABLE ai_draft (
    id                      BIGSERIAL PRIMARY KEY,
    case_id                 BIGINT       NOT NULL REFERENCES case_record (id),
    input_snapshot_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    raw_output              TEXT         NOT NULL,
    analyst_final_text      TEXT,
    model_metadata_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    generated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    edited_by               VARCHAR(128),
    edited_at               TIMESTAMPTZ,
    status                  VARCHAR(32)  NOT NULL
);

CREATE INDEX idx_ai_draft_case_id ON ai_draft (case_id);
CREATE INDEX idx_ai_draft_status ON ai_draft (status);

CREATE TABLE sanctions_entry (
    id                      BIGSERIAL PRIMARY KEY,
    source_unique_id        VARCHAR(128) NOT NULL,
    name                    VARCHAR(512) NOT NULL,
    entity_type             VARCHAR(32)  NOT NULL,
    aliases_json            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    identifiers_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    measures_json           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    source_updated_at       TIMESTAMPTZ,
    import_batch_id         VARCHAR(64),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_sanctions_entry_source_unique_id ON sanctions_entry (source_unique_id);
CREATE INDEX idx_sanctions_entry_name ON sanctions_entry (name);
CREATE INDEX idx_sanctions_entry_active ON sanctions_entry (active);
CREATE INDEX idx_sanctions_entry_import_batch_id ON sanctions_entry (import_batch_id);

CREATE TABLE sanctions_hit (
    id                          BIGSERIAL PRIMARY KEY,
    trigger_type                VARCHAR(64)    NOT NULL,
    payment_txn_id              BIGINT         REFERENCES txn (id),
    customer_id                 BIGINT         REFERENCES customer (id),
    screened_name               VARCHAR(512)   NOT NULL,
    sanctions_entry_id          BIGINT         NOT NULL REFERENCES sanctions_entry (id),
    name_similarity             NUMERIC(4, 3)  NOT NULL,
    status                      VARCHAR(32)    NOT NULL,
    match_details_snapshot      JSONB          NOT NULL DEFAULT '{}'::jsonb,
    sanctions_entry_snapshot    JSONB          NOT NULL DEFAULT '{}'::jsonb,
    resolution_rationale        TEXT,
    resolved_by                 VARCHAR(128),
    resolved_at                 TIMESTAMPTZ
);

CREATE INDEX idx_sanctions_hit_status ON sanctions_hit (status);
CREATE INDEX idx_sanctions_hit_customer_id ON sanctions_hit (customer_id);
CREATE INDEX idx_sanctions_hit_payment_txn_id ON sanctions_hit (payment_txn_id);
CREATE INDEX idx_sanctions_hit_sanctions_entry_id ON sanctions_hit (sanctions_entry_id);
CREATE INDEX idx_sanctions_hit_trigger_type ON sanctions_hit (trigger_type);

-- Support: FX rates (composite PK: currency + rate_date)
CREATE TABLE fx_rate (
    currency                VARCHAR(3)          NOT NULL,
    rate_date               DATE             NOT NULL,
    rate_to_gbp             NUMERIC(18, 8)   NOT NULL,
    fetched_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (currency, rate_date)
);

CREATE INDEX idx_fx_rate_rate_date ON fx_rate (rate_date);

-- Support: country metadata
CREATE TABLE country_info (
    iso2                    VARCHAR(2)       PRIMARY KEY,
    name                    VARCHAR(255)  NOT NULL,
    region                  VARCHAR(128),
    capital                 VARCHAR(128),
    raw_json                JSONB         NOT NULL DEFAULT '{}'::jsonb
);
