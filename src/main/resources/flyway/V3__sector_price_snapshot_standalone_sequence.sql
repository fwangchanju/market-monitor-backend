ALTER TABLE sector_price_snapshot ALTER COLUMN id DROP IDENTITY;
CREATE SEQUENCE sector_price_snapshot_id_seq INCREMENT BY 500 OWNED BY sector_price_snapshot.id;
SELECT setval('sector_price_snapshot_id_seq', (SELECT COALESCE(MAX(id), 0) FROM sector_price_snapshot) + 1000, false);
ALTER TABLE sector_price_snapshot ALTER COLUMN id SET DEFAULT nextval('sector_price_snapshot_id_seq');
