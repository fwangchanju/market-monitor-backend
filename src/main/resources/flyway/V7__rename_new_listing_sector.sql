-- 신규 종목이 자동 배정되는 최상위 업종 이름을 "신규상장"에서 "신규 상장"으로 바꾼다.
-- 같은 사용자에게 이미 "신규 상장"이 있으면(uk_custom_sector_user_name 충돌) 그 사용자는 건드리지 않는다.
UPDATE custom_sector
SET name = '신규 상장',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '신규상장'
  AND NOT EXISTS (
      SELECT 1
      FROM custom_sector other
      WHERE other.user_id = custom_sector.user_id
        AND other.name = '신규 상장'
  );
