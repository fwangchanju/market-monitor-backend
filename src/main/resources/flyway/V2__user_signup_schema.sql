DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM custom_scale_threshold) THEN
        RAISE EXCEPTION 'custom_scale_threshold must contain the current operational values before V2';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM custom_value_tier_threshold) THEN
        RAISE EXCEPTION 'custom_value_tier_threshold must contain the current operational values before V2';
    END IF;
END;
$$;

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    issuer VARCHAR(255),
    sub VARCHAR(255),
    email VARCHAR(320),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_issuer_sub UNIQUE (issuer, sub),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE id = 999999) THEN
        RAISE EXCEPTION 'reserved migration user id 999999 already exists';
    END IF;
END;
$$;

-- The explicit placeholder does not advance the identity sequence; signup starts at 1.
INSERT INTO users (id, issuer, sub, email, role)
OVERRIDING SYSTEM VALUE
VALUES (999999, NULL, NULL, NULL, 'USER');

CREATE TABLE industry_info (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_industry_info PRIMARY KEY (id),
    CONSTRAINT uk_industry_info_name UNIQUE (name)
);

INSERT INTO industry_info (name)
SELECT DISTINCT BTRIM(industry_name)
FROM stock_info
WHERE active
  AND market_code IN ('0', '10')
  AND industry_name IS NOT NULL
  AND BTRIM(industry_name) <> '';

ALTER TABLE stock_info ADD COLUMN industry_id BIGINT;

UPDATE stock_info AS stock
SET industry_id = industry.id
FROM industry_info AS industry
WHERE stock.active
  AND stock.market_code IN ('0', '10')
  AND stock.industry_name IS NOT NULL
  AND BTRIM(stock.industry_name) = industry.name;

ALTER TABLE stock_info
    ADD CONSTRAINT fk_stock_info_industry
        FOREIGN KEY (industry_id) REFERENCES industry_info (id);

CREATE INDEX idx_stock_info_industry_id ON stock_info (industry_id);

CREATE TABLE default_scale_threshold (
    threshold_percent NUMERIC(5,2) NOT NULL,
    color VARCHAR(7) NOT NULL,
    color_label VARCHAR(20),
    CONSTRAINT pk_default_scale_threshold PRIMARY KEY (threshold_percent)
);

INSERT INTO default_scale_threshold (threshold_percent, color, color_label)
SELECT threshold_percent, color, color_label
FROM custom_scale_threshold
ORDER BY threshold_percent;

CREATE TABLE default_value_tier_threshold (
    label VARCHAR(50) NOT NULL,
    threshold_value BIGINT NOT NULL,
    is_excluded_by_default BOOLEAN NOT NULL,
    CONSTRAINT pk_default_value_tier_threshold PRIMARY KEY (label)
);

INSERT INTO default_value_tier_threshold (label, threshold_value, is_excluded_by_default)
SELECT label, threshold_value, is_excluded_by_default
FROM custom_value_tier_threshold
ORDER BY threshold_value, id;

ALTER TABLE custom_sector
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 999999;

ALTER TABLE custom_stock_sector
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 999999;

ALTER TABLE custom_snapshot
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 999999;

ALTER TABLE custom_scale_threshold
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 999999;

ALTER TABLE custom_value_tier_threshold
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 999999;

CREATE TABLE custom_stock_alias (
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    alias VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_custom_stock_alias PRIMARY KEY (user_id, stock_code),
    CONSTRAINT fk_custom_stock_alias_stock
        FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code),
    CONSTRAINT fk_custom_stock_alias_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED
);

INSERT INTO custom_stock_alias (user_id, stock_code, alias, created_at, updated_at)
SELECT 999999, stock_code, alias, created_at, updated_at
FROM custom_stock_sector
WHERE alias IS NOT NULL;

CREATE TABLE user_preference (
    user_id BIGINT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_preference PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preference_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED
);

INSERT INTO user_preference (user_id, payload) VALUES (999999, '{}'::JSONB);

CREATE TABLE user_refresh_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_user_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_user_refresh_token_user_expiry ON user_refresh_token (user_id, expires_at);

ALTER TABLE custom_sector DROP CONSTRAINT fk_custom_sector_parent;
ALTER TABLE custom_sector DROP CONSTRAINT fk_custom_sector_snapshot;
ALTER TABLE custom_sector DROP CONSTRAINT uk_custom_sector_name;
ALTER TABLE custom_stock_sector DROP CONSTRAINT fk_custom_stock_sector_sector;
ALTER TABLE custom_stock_sector DROP CONSTRAINT pk_custom_stock_sector;
ALTER TABLE custom_scale_threshold DROP CONSTRAINT uk_custom_scale_threshold_percent;
ALTER TABLE custom_value_tier_threshold DROP CONSTRAINT uk_custom_value_tier_threshold_label;

ALTER TABLE custom_snapshot
    ADD CONSTRAINT uk_custom_snapshot_id_user UNIQUE (id, user_id),
    ADD CONSTRAINT fk_custom_snapshot_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE custom_sector
    ADD CONSTRAINT uk_custom_sector_user_name UNIQUE (user_id, name),
    ADD CONSTRAINT uk_custom_sector_id_user UNIQUE (id, user_id);

ALTER TABLE custom_sector
    ADD CONSTRAINT fk_custom_sector_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_custom_sector_parent_user
        FOREIGN KEY (parent_id, user_id) REFERENCES custom_sector (id, user_id)
        DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_custom_sector_snapshot_user
        FOREIGN KEY (snapshot_id, user_id) REFERENCES custom_snapshot (id, user_id)
        ON DELETE SET NULL (snapshot_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE custom_stock_sector
    ADD CONSTRAINT pk_custom_stock_sector PRIMARY KEY (user_id, stock_code),
    ADD CONSTRAINT fk_custom_stock_sector_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_custom_stock_sector_sector_user
        FOREIGN KEY (sector_id, user_id) REFERENCES custom_sector (id, user_id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE custom_scale_threshold
    ADD CONSTRAINT uk_custom_scale_threshold_user_percent UNIQUE (user_id, threshold_percent),
    ADD CONSTRAINT fk_custom_scale_threshold_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE custom_value_tier_threshold
    ADD CONSTRAINT uk_custom_value_tier_threshold_user_label UNIQUE (user_id, label),
    ADD CONSTRAINT fk_custom_value_tier_threshold_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_custom_sector_user_parent ON custom_sector (user_id, parent_id);
CREATE INDEX idx_custom_sector_user_snapshot ON custom_sector (user_id, snapshot_id);
CREATE INDEX idx_custom_snapshot_user_id ON custom_snapshot (user_id, id);
CREATE INDEX idx_custom_stock_sector_user_sector ON custom_stock_sector (user_id, sector_id);
