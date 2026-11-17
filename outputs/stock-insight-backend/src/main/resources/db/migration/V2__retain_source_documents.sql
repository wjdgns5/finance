-- 기존 자료와 금액은 보존합니다. 새 원본·미매핑 기록만 nullable 컬럼에 추가합니다.
ALTER TABLE financial_statement
    ADD COLUMN raw_payload LONGTEXT NULL,
    ADD COLUMN normalization_notes TEXT NULL;
