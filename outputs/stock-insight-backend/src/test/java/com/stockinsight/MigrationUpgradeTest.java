package com.stockinsight;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MYSQL_IT_UPGRADE_URL", matches = ".+")
class MigrationUpgradeTest {
    @Test
    void v2AddsSourceColumnsAndPreservesExistingV1Values() throws Exception {
        String url = System.getenv("MYSQL_IT_UPGRADE_URL");
        String user = System.getenv().getOrDefault("MYSQL_IT_USER", "stock_insight");
        String password = System.getenv().getOrDefault("MYSQL_IT_PASSWORD", "local_mock_password");
        Flyway.configure().dataSource(url, user, password).target("1").load().migrate();
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            sql.executeUpdate("INSERT INTO company(stock_code,corp_code,name,market,industry_code,industry_name,fiscal_month,supported,data_mode,source,collected_at) VALUES('777777','00777777','마이그레이션검증','KOSPI','C','제조',12,1,'MOCK','MOCK','2026-11-17')");
            sql.executeUpdate("INSERT INTO financial_statement(company_id,fiscal_year,report_code,basis,receipt_no,filed_at,period_start,period_end,currency,source,collected_at) SELECT id,2025,'11011','CFS','UPGRADE-TEST','2026-03-20','2025-01-01','2025-12-31','KRW','MOCK','2026-11-17' FROM company WHERE stock_code='777777'");
            sql.executeUpdate("INSERT INTO financial_account(statement_id,standard_code,amount,original_account_id,original_account_name,original_amount) SELECT id,'REVENUE',123456789.123456,'MOCK_REVENUE','매출액','123456789.123456' FROM financial_statement WHERE receipt_no='UPGRADE-TEST'");
        }
        Flyway.configure().dataSource(url, user, password).load().migrate();
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement();
             var row = sql.executeQuery("SELECT a.amount,s.receipt_no,s.raw_payload,s.normalization_notes FROM financial_account a JOIN financial_statement s ON s.id=a.statement_id WHERE s.receipt_no='UPGRADE-TEST'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getBigDecimal("amount")).isEqualByComparingTo("123456789.123456");
            assertThat(row.getString("receipt_no")).isEqualTo("UPGRADE-TEST");
            assertThat(row.getString("raw_payload")).isNull();
            assertThat(row.getString("normalization_notes")).isNull();
        }
    }
}
