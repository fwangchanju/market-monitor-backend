-- 1) 죽은 테이블. 등록·해제 API만 있고 지도 트리 경로도 프론트 화면도 이 테이블을 읽지 않는다.
--    stock_info 를 가리키는 쪽이라 먼저 지워도 된다
DROP TABLE market_map_excluded_stock;

-- 2) 테이블 이름
ALTER TABLE market_map_category         RENAME TO custom_sector;
ALTER TABLE market_map_stock_category   RENAME TO custom_stock_sector;
ALTER TABLE market_map_category_version RENAME TO custom_snapshot;
ALTER TABLE market_map_scale_threshold  RENAME TO custom_scale_threshold;
ALTER TABLE market_value_tier_threshold RENAME TO custom_value_tier_threshold;

-- 3) 제약 이름. PK·UK 는 같은 이름의 인덱스도 함께 바뀐다
ALTER TABLE custom_sector RENAME CONSTRAINT pk_market_map_category         TO pk_custom_sector;
ALTER TABLE custom_sector RENAME CONSTRAINT uk_market_map_category_name    TO uk_custom_sector_name;
ALTER TABLE custom_sector RENAME CONSTRAINT fk_market_map_category_parent  TO fk_custom_sector_parent;
ALTER TABLE custom_sector RENAME CONSTRAINT fk_market_map_category_version TO fk_custom_sector_snapshot;

ALTER TABLE custom_stock_sector RENAME CONSTRAINT pk_market_map_stock_category          TO pk_custom_stock_sector;
ALTER TABLE custom_stock_sector RENAME CONSTRAINT fk_market_map_stock_category_stock    TO fk_custom_stock_sector_stock;
ALTER TABLE custom_stock_sector RENAME CONSTRAINT fk_market_map_stock_category_category TO fk_custom_stock_sector_sector;

ALTER TABLE custom_snapshot RENAME CONSTRAINT pk_market_map_category_version TO pk_custom_snapshot;

ALTER TABLE custom_scale_threshold RENAME CONSTRAINT pk_market_map_scale_threshold         TO pk_custom_scale_threshold;
ALTER TABLE custom_scale_threshold RENAME CONSTRAINT uk_market_map_scale_threshold_percent TO uk_custom_scale_threshold_percent;

ALTER TABLE custom_value_tier_threshold RENAME CONSTRAINT pk_market_value_tier_threshold       TO pk_custom_value_tier_threshold;
ALTER TABLE custom_value_tier_threshold RENAME CONSTRAINT uk_market_value_tier_threshold_label TO uk_custom_value_tier_threshold_label;

-- 4) 컬럼 이름. 저장본 테이블을 가리키는 FK 컬럼 (저장본이 버전 이력이 아니라 이름 붙인 스냅샷이라서)
ALTER TABLE custom_sector RENAME COLUMN version_id TO snapshot_id;

-- 5) identity 시퀀스 이름. 연결·현재값은 그대로다 (custom_stock_sector 는 PK 가 stock_code 라 시퀀스가 없다)
ALTER SEQUENCE market_map_category_id_seq         RENAME TO custom_sector_id_seq;
ALTER SEQUENCE market_map_category_version_id_seq RENAME TO custom_snapshot_id_seq;
ALTER SEQUENCE market_map_scale_threshold_id_seq  RENAME TO custom_scale_threshold_id_seq;
ALTER SEQUENCE market_value_tier_threshold_id_seq RENAME TO custom_value_tier_threshold_id_seq;
