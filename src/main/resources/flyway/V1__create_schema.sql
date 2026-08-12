CREATE TABLE stock_info (
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    market_code VARCHAR(5),
    category_name VARCHAR(50),
    list_count BIGINT NOT NULL,
    last_price DECIMAL(19,2),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_info PRIMARY KEY (stock_code)
);

CREATE TABLE market_map_excluded_stock (
    stock_code VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_map_excluded_stock PRIMARY KEY (stock_code),
    CONSTRAINT fk_market_map_excluded_stock_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE allowed_ip (
    ip VARCHAR(45) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_allowed_ip PRIMARY KEY (ip)
);

CREATE TABLE market_map_category_version (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    label VARCHAR(50) NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_map_category_version PRIMARY KEY (id)
);

CREATE TABLE market_map_category (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    parent_id BIGINT,
    version_id BIGINT,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    depth INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_map_category PRIMARY KEY (id),
    CONSTRAINT uk_market_map_category_name UNIQUE (name),
    CONSTRAINT fk_market_map_category_parent FOREIGN KEY (parent_id) REFERENCES market_map_category (id),
    CONSTRAINT fk_market_map_category_version FOREIGN KEY (version_id) REFERENCES market_map_category_version (id) ON DELETE SET NULL
);

CREATE TABLE market_map_stock_category (
    stock_code VARCHAR(20) NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_map_stock_category PRIMARY KEY (stock_code),
    CONSTRAINT fk_market_map_stock_category_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code),
    CONSTRAINT fk_market_map_stock_category_category FOREIGN KEY (category_id) REFERENCES market_map_category (id)
);

CREATE TABLE admin_token (
    token VARCHAR(64) NOT NULL,
    last_ip VARCHAR(45),
    label VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_admin_token PRIMARY KEY (token)
);

CREATE TABLE watch_stock (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    is_primary BOOLEAN NOT NULL,
    register_by VARCHAR(20) NOT NULL,
    holding_rank INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_watch_stock PRIMARY KEY (id),
    CONSTRAINT uk_watch_stock_stock UNIQUE (stock_code),
    CONSTRAINT fk_watch_stock_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE market_overview_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    index_value DECIMAL(19,4) NOT NULL,
    change_value DECIMAL(19,4) NOT NULL,
    change_rate DECIMAL(9,4) NOT NULL,
    trading_value DECIMAL(19,2) NOT NULL,
    market_status VARCHAR(30) NOT NULL,
    advancers INT NOT NULL,
    decliners INT NOT NULL,
    unchanged_count INT NOT NULL,
    upper_limit_count INT NOT NULL,
    lower_limit_count INT NOT NULL,
    last_collected_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_overview_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_market_overview_snapshot UNIQUE (market_type, snapshot_time)
);

CREATE TABLE investor_trading_summary_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    investor VARCHAR(20) NOT NULL,
    amt_qty VARCHAR(20) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    buy_amount DECIMAL(19,2) NOT NULL,
    sell_amount DECIMAL(19,2) NOT NULL,
    net_buy_amount DECIMAL(19,2) NOT NULL,
    last_collected_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_investor_trading_summary_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_investor_trading_summary_snapshot UNIQUE (market_type, investor, amt_qty, snapshot_time)
);

CREATE TABLE intraday_investor_ranking_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    investor VARCHAR(20) NOT NULL,
    ranking_type VARCHAR(20) NOT NULL,
    amt_qty VARCHAR(20) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    rank_no INT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    net_buy_amount DECIMAL(19,2) NOT NULL,
    sel_qty BIGINT NOT NULL,
    traded_volume BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_intraday_investor_ranking_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_intraday_investor_ranking_snapshot UNIQUE (market_type, investor, ranking_type, amt_qty, stock_code, snapshot_time),
    CONSTRAINT fk_intraday_investor_ranking_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE program_trading_ranking_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    amt_qty VARCHAR(20) NOT NULL,
    ranking_type VARCHAR(20) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    exchange_type VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    program_buy_amount DECIMAL(19,2) NOT NULL,
    program_sell_amount DECIMAL(19,2) NOT NULL,
    program_net_buy_amount DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_program_trading_ranking_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_program_trading_ranking_snapshot UNIQUE (market_type, amt_qty, ranking_type, stock_code, exchange_type, snapshot_time),
    CONSTRAINT fk_program_trading_ranking_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE program_trading_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    program_buy_amount DECIMAL(19,2) NOT NULL,
    program_sell_amount DECIMAL(19,2) NOT NULL,
    program_net_buy_amount DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_program_trading_history PRIMARY KEY (id),
    CONSTRAINT uk_program_trading_history UNIQUE (stock_code, snapshot_time),
    CONSTRAINT fk_program_trading_history_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE program_trading_daily (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    program_buy_amount DECIMAL(19,2) NOT NULL,
    program_sell_amount DECIMAL(19,2) NOT NULL,
    program_net_buy_amount DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_program_trading_daily PRIMARY KEY (id),
    CONSTRAINT uk_program_trading_daily UNIQUE (stock_code, trade_date),
    CONSTRAINT fk_program_trading_daily_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE short_selling_daily (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    close_price DECIMAL(19,2) NOT NULL,
    price_change DECIMAL(19,2) NOT NULL,
    change_rate DECIMAL(9,4) NOT NULL,
    trading_volume BIGINT NOT NULL,
    short_volume BIGINT NOT NULL,
    cumulative_short_volume BIGINT NOT NULL,
    short_ratio DECIMAL(9,4) NOT NULL,
    short_amount DECIMAL(19,2) NOT NULL,
    short_avg_price DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_short_selling_daily PRIMARY KEY (id),
    CONSTRAINT uk_short_selling_daily UNIQUE (stock_code, trade_date),
    CONSTRAINT fk_short_selling_daily_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE index_contribution_ranking_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    rank_no INT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    market_code VARCHAR(5),
    contribution_score DECIMAL(19,4) NOT NULL,
    price_change_rate DECIMAL(9,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_index_contribution_ranking_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_index_contribution_ranking_snapshot UNIQUE (market_type, stock_code, snapshot_time),
    CONSTRAINT fk_index_contribution_ranking_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
);

CREATE TABLE sector_price_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    exchange_type VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    current_price DECIMAL(19,4) NOT NULL,
    change_value DECIMAL(19,4) NOT NULL,
    change_rate DECIMAL(9,4) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sector_price_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_sector_price_snapshot UNIQUE (market_type, stock_code, exchange_type, snapshot_time)
);

CREATE INDEX idx_market_overview_snapshot_time ON market_overview_snapshot (snapshot_time);
CREATE INDEX idx_investor_trading_summary_snapshot_time ON investor_trading_summary_snapshot (snapshot_time);
CREATE INDEX idx_intraday_investor_ranking_snapshot_time ON intraday_investor_ranking_snapshot (snapshot_time);
CREATE INDEX idx_program_trading_ranking_snapshot_time ON program_trading_ranking_snapshot (snapshot_time);
CREATE INDEX idx_program_trading_history_stock_time ON program_trading_history (stock_code, snapshot_time DESC);
CREATE INDEX idx_program_trading_daily_stock_date ON program_trading_daily (stock_code, trade_date DESC);
CREATE INDEX idx_short_selling_daily_stock_date ON short_selling_daily (stock_code, trade_date DESC);
CREATE INDEX idx_index_contribution_ranking_snapshot_time ON index_contribution_ranking_snapshot (snapshot_time);
CREATE INDEX idx_sector_price_snapshot_time ON sector_price_snapshot (snapshot_time);
