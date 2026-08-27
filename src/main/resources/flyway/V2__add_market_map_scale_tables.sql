CREATE TABLE market_map_scale_threshold (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    threshold_percent NUMERIC(5,2) NOT NULL,
    color VARCHAR(7) NOT NULL,
    color_label VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_map_scale_threshold PRIMARY KEY (id),
    CONSTRAINT uk_market_map_scale_threshold_percent UNIQUE (threshold_percent)
);
