package com.stockinsight;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockinsight.provider.StubProviders;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MYSQL_IT_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=real", "app.data-mode=REAL"})
@Import(RealDataIntegrationTest.FixedTime.class)
class RealDataIntegrationTest {
    private static final StubProviders UPSTREAM = new StubProviders();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock contractClock() {
            return Clock.fixed(Instant.parse("2026-11-17T02:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void realOpenApiIncludesManagementEndpointsWithoutCollectingOrExposingKeys() {
        var response = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var doc = response.getBody();
        assertThat(doc.at("/info/description").asText()).contains("현재 데이터 모드: REAL");
        assertThat(doc.path("paths").size()).isEqualTo(7);
        var sync = doc.path("paths").path("/api/v1/companies/{stockCode}/sync").path("post");
        assertThat(sync.path("operationId").asText()).isEqualTo("syncCompany");
        assertThat(sync.path("description").asText()).contains("DB", "outcomes");
        assertThat(sync.path("parameters").get(0).path("example").asText()).isEqualTo("000270");
        assertThat(doc.path("paths").path("/api/v1/companies/{stockCode}/financials/{year}/source").has("get")).isTrue();
        var source = doc.path("paths").path("/api/v1/companies/{stockCode}/financials/{year}/source").path("get");
        String sourceRef = source.at("/responses/200/content/application~1json/schema/$ref").asText();
        assertThat(sourceRef).startsWith("#/components/schemas/");
        var sourceData = doc.at(sourceRef.substring(1)).at("/properties/data");
        assertThat(sourceData.path("type").asText()).isEqualTo("object");
        assertThat(sourceData.path("additionalProperties").asBoolean()).isTrue();
        assertThat(doc.toString()).doesNotContain("contract-test-key-not-real", "contract-fsc-key-not-real");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE data_mode='REAL'", Integer.class)).isZero();
        assertThat(UPSTREAM.calls("/dart/corpCode.xml")).isZero();
        assertThat(UPSTREAM.calls("/dart/fnlttSinglAcntAll.json")).isZero();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_IT_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_IT_USER", "stock_insight"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_IT_PASSWORD", "local_mock_password"));
        registry.add("app.dart.base-url", () -> UPSTREAM.base() + "/dart");
        registry.add("app.dart.api-key", () -> "contract-test-key-not-real");
        registry.add("app.fsc.base-url", () -> UPSTREAM.base() + "/fsc");
        registry.add("app.fsc.price-path", () -> "/getStockPriceInfo_V2");
        registry.add("app.fsc.api-key", () -> "contract-fsc-key-not-real");
    }

    @BeforeEach void resetDedicatedTestRows() {
        UPSTREAM.reset();
        jdbc.update("DELETE h FROM data_sync_history h JOIN company c ON c.id=h.company_id WHERE c.data_mode='REAL'");
        jdbc.update("DELETE p FROM market_price p JOIN company c ON c.id=p.company_id WHERE c.data_mode='REAL'");
        jdbc.update("DELETE a FROM financial_account a JOIN financial_statement s ON s.id=a.statement_id JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'");
        jdbc.update("DELETE s FROM financial_statement s JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'");
        jdbc.update("DELETE FROM company WHERE data_mode='REAL'");
    }

    @AfterAll static void close() { UPSTREAM.close(); }

    @Test
    void searchCollectPersistCalculateAndReuseWithSourceTrace() {
        var search = http.getForObject("/api/v1/companies?query=계약검증", JsonNode.class);
        assertThat(search.path("data").get(0).path("stockCode").asText()).isEqualTo("123456");
        assertThat(search.path("meta").path("mode").asText()).isEqualTo("REAL");
        var financials = http.getForObject("/api/v1/companies/123456/financials", JsonNode.class);
        assertThat(financials.path("data").path("years").get(0).path("normalizationWarnings").size()).isGreaterThan(0);
        int calls = UPSTREAM.calls("/dart/fnlttSinglAcntAll.json");
        var metrics = http.getForObject("/api/v1/companies/123456/metrics", JsonNode.class);
        assertThat(UPSTREAM.calls("/dart/fnlttSinglAcntAll.json")).isEqualTo(calls);
        assertThat(metric(metrics, "ROE").path("value").asText()).isEqualTo("15.000000");
        assertThat(metric(metrics, "PBR").path("value").asText()).isEqualTo("13.500000");
        assertThat(metric(metrics, "REVENUE_GROWTH").path("reasonCode").asText()).isEqualTo("MISSING_PREVIOUS_YEAR");
        assertThat(metrics.path("meta").path("market").path("closePriceSource").asText()).isEqualTo("FSC");
        assertThat(metrics.path("meta").path("financialPeriods").get(0).path("receiptNo").asText()).isEqualTo("20260320000002");
        var source = http.getForObject("/api/v1/companies/123456/financials/2025/source", JsonNode.class);
        assertThat(source.path("data").path("financials").path("list").get(0).path("thstrm_amount").asText()).isEqualTo("1,200");
        assertThat(source.toString()).doesNotContain("contract-test-key-not-real", "crtfc_key", "serviceKey");
        var mock = http.getForObject("/api/v1/companies?query=990001", JsonNode.class);
        assertThat(mock.path("data").size()).isZero();
    }

    @Test
    void refreshIsIdempotentAndUpdatesWithoutDuplicateAccounts() {
        http.getForObject("/api/v1/companies/123456/financials", JsonNode.class);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM financial_account a JOIN financial_statement s ON s.id=a.statement_id JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'", Integer.class);
        var updated = http.postForObject("/api/v1/companies/123456/sync", null, JsonNode.class);
        assertThat(updated.path("data").path("outcomes").get(0).path("status").asText()).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_account a JOIN financial_statement s ON s.id=a.statement_id JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'", Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_statement s JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_price p JOIN company c ON c.id=p.company_id WHERE c.data_mode='REAL'", Integer.class)).isEqualTo(1);
    }

    @Test
    void refreshedOfsDoesNotLeaveOlderCfsSelected() {
        http.getForObject("/api/v1/companies/123456/financials", JsonNode.class);
        UPSTREAM.onlyOfs = true;
        http.postForObject("/api/v1/companies/123456/sync", null, JsonNode.class);
        var updated = http.getForObject("/api/v1/companies/123456/financials", JsonNode.class);
        assertThat(updated.path("data").path("years").get(0).path("basis").asText()).isEqualTo("OFS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_statement s JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedRefreshKeepsLastGoodDataAndMarksStale() {
        var original = http.getForObject("/api/v1/companies/123456/financials", JsonNode.class);
        UPSTREAM.rateLimited = true;
        var failed = http.postForObject("/api/v1/companies/123456/sync", null, JsonNode.class);
        assertThat(failed.path("data").path("outcomes").get(0).path("errorCode").asText()).isEqualTo("DART_RATE_LIMIT");
        assertThat(failed.path("meta").path("stale").asBoolean()).isTrue();
        var preserved = http.getForObject("/api/v1/companies/123456/financials", JsonNode.class);
        assertThat(preserved.path("data")).isEqualTo(original.path("data"));
        assertThat(preserved.path("meta").path("stale").asBoolean()).isTrue();
        assertThat(failed.toString()).doesNotContain("sensitive upstream text");
    }

    @Test
    void missingQuoteOnlyBlocksPriceMetricsAndDoesNotCreateFakeSnapshot() {
        UPSTREAM.noPrice = true;
        var metrics = http.getForObject("/api/v1/companies/123456/metrics", JsonNode.class);
        assertThat(metrics.path("meta").path("market").isNull()).isTrue();
        assertThat(metric(metrics, "ROA").path("value").asText()).isEqualTo("8.000000");
        assertThat(metric(metrics, "BPS").path("value").asText()).isEqualTo("6.666667");
        assertThat(metric(metrics, "PER").path("reasonCode").asText()).isEqualTo("MISSING_PRICE");
        assertThat(metric(metrics, "PBR").path("reasonCode").asText()).isEqualTo("MISSING_PRICE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_price p JOIN company c ON c.id=p.company_id WHERE c.data_mode='REAL'", Integer.class)).isZero();
    }

    @Test
    void initialCollectionFailureIsReportedAndRetryDoesNotHammerProvider() {
        UPSTREAM.rateLimited = true;
        var first = http.getForEntity("/api/v1/companies/123456/financials", JsonNode.class);
        assertThat(first.getStatusCode().value()).isEqualTo(503);
        assertThat(first.getBody().path("error").path("code").asText()).isEqualTo("DART_RATE_LIMIT");
        int calls = UPSTREAM.calls("/dart/fnlttSinglAcntAll.json");
        assertThat(http.getForEntity("/api/v1/companies/123456/financials", JsonNode.class).getStatusCode().value()).isEqualTo(503);
        assertThat(UPSTREAM.calls("/dart/fnlttSinglAcntAll.json")).isEqualTo(calls);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_statement s JOIN company c ON c.id=s.company_id WHERE c.data_mode='REAL'", Integer.class)).isZero();
    }

    private JsonNode metric(JsonNode body, String code) {
        for (JsonNode metric : body.path("data").path("metrics")) {
            if (metric.path("code").asText().equals(code)) { return metric; }
        }
        throw new AssertionError(code);
    }
}
