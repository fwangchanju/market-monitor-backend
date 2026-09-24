-- 1) 집계 테이블. market_map_category 와 market_value_tier_threshold 를 FK 로 가리키는 쪽이라 먼저 지워도 된다
DROP TABLE IF EXISTS market_map_category_change_rate_snapshot;

-- 2) 옛 카테고리 테이블. 코드는 쓰지 않는다. 이 테이블이 market_map_category_id_seq 이름을 쥐고 있어서
--    지금 market_map_category 가 _seq1 을 쓰고 있다. CASCADE 는 쓰지 않는다 — 누가 가리키고 있으면 실패해야 한다
DROP TABLE IF EXISTS market_map_category_old;

-- 3) '1' 꼬리 시퀀스 이름 바로잡기. 이름만 바뀌고 identity 연결·현재값은 그대로다.
--    로컬·새 DB에는 _seq1 이 없으므로 IF EXISTS
ALTER SEQUENCE IF EXISTS market_map_category_id_seq1              RENAME TO market_map_category_id_seq;
ALTER SEQUENCE IF EXISTS program_trading_ranking_snapshot_id_seq1 RENAME TO program_trading_ranking_snapshot_id_seq;
ALTER SEQUENCE IF EXISTS sector_price_snapshot_id_seq1            RENAME TO sector_price_snapshot_id_seq;
