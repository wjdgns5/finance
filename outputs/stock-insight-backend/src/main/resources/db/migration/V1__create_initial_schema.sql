CREATE TABLE company (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_code VARCHAR(6) NOT NULL,
    corp_code VARCHAR(8) NOT NULL,
    name VARCHAR(100) NOT NULL,
    market VARCHAR(16) NOT NULL,
    industry_code VARCHAR(16) NOT NULL,
    industry_name VARCHAR(100) NOT NULL,
    fiscal_month INT NOT NULL,
    has_preferred_shares BIT NULL,
    supported BIT NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_mode_stock UNIQUE (data_mode, stock_code),
    CONSTRAINT uk_company_mode_corp UNIQUE (data_mode, corp_code),
    CONSTRAINT ck_company_fiscal_month CHECK (fiscal_month BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE financial_statement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    fiscal_year INT NOT NULL,
    report_code VARCHAR(5) NOT NULL,
    basis VARCHAR(8) NOT NULL,
    receipt_no VARCHAR(64) NOT NULL,
    filed_at DATE NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    source VARCHAR(16) NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    issued_common_shares BIGINT NULL,
    treasury_common_shares BIGINT NULL,
    outstanding_common_shares BIGINT NULL,
    preferred_shares BIGINT NULL,
    shares_as_of DATE NULL,
    shares_receipt_no VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_statement_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT uk_statement_period_basis UNIQUE (company_id, fiscal_year, report_code, basis),
    CONSTRAINT ck_statement_annual CHECK (report_code = '11011'),
    CONSTRAINT ck_statement_dates CHECK (period_end >= period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE financial_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    standard_code VARCHAR(48) NOT NULL,
    amount DECIMAL(30,6) NULL,
    original_account_id VARCHAR(256) NOT NULL,
    original_account_name VARCHAR(256) NOT NULL,
    original_account_detail VARCHAR(1000) NULL,
    original_amount VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_account_statement FOREIGN KEY (statement_id) REFERENCES financial_statement (id),
    CONSTRAINT uk_account_statement_code UNIQUE (statement_id, standard_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE market_price (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    close_price DECIMAL(30,6) NOT NULL,
    close_price_as_of DATE NOT NULL,
    close_price_source VARCHAR(16) NOT NULL,
    market_cap DECIMAL(30,0) NULL,
    market_cap_as_of DATE NULL,
    market_cap_source VARCHAR(16) NULL,
    currency VARCHAR(3) NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_market_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT uk_market_snapshot UNIQUE (company_id, close_price_as_of, close_price_source),
    CONSTRAINT ck_market_close_positive CHECK (close_price > 0),
    CONSTRAINT ck_market_cap_metadata CHECK (
        (market_cap IS NULL AND market_cap_as_of IS NULL AND market_cap_source IS NULL)
        OR (market_cap IS NOT NULL AND market_cap >= 0 AND market_cap_as_of IS NOT NULL AND market_cap_source IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE data_sync_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    sync_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    items_saved INT NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sync_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT ck_sync_dates CHECK (completed_at >= started_at),
    INDEX ix_sync_company_type_completed (company_id, sync_type, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
