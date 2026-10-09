-- 키움이 업종명을 비워 보내는 종목의 거래소 분류 보정표. 키움 업종이 없을 때만 읽는 쪽에서 이 표를 쓴다.
-- 종목을 더하려면 INSERT 한 줄만 추가하면 된다. 키움이 값을 채워 주면 저절로 키움 값이 우선한다.
CREATE TABLE stock_industry_override (
    stock_code VARCHAR(20) PRIMARY KEY,
    industry_id BIGINT NOT NULL,
    CONSTRAINT fk_stock_industry_override_industry FOREIGN KEY (industry_id) REFERENCES industry_info (id)
);

-- 시가총액이 큰 순서로 제주은행까지만 채웠다. 나머지 미분류(외국 기업, 소형주)는 의도적으로 그대로 둔다.
INSERT INTO stock_industry_override (stock_code, industry_id)
SELECT v.stock_code, i.id
FROM (VALUES
    ('024110', '금융'), -- 기업은행
    ('323410', '금융'), -- 카카오뱅크
    ('279570', '금융'), -- 케이뱅크
    ('006220', '금융'), -- 제주은행
    ('950260', '제약'), -- 인제니아테라퓨틱스
    ('950160', '제약'), -- 코오롱티슈진
    ('950210', '제약')  -- 프레스티지바이오파마
) AS v (stock_code, industry_name)
JOIN industry_info i ON i.name = v.industry_name
ON CONFLICT (stock_code) DO NOTHING;
