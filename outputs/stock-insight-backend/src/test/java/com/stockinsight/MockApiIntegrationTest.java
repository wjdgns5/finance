package com.stockinsight;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockinsight.service.MockSeedService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MYSQL_IT_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.profiles.active=mock")
class MockApiIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockSeedService seed;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_IT_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_IT_USER", "stock_insight"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_IT_PASSWORD", "local_mock_password"));
    }

    @Test
    void openApiIncludesOnlyMockOperationsWithConstraintsAndErrorContract() {
        var response = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var doc = response.getBody();
        assertThat(doc.path("openapi").asText()).startsWith("3.0.");
        assertThat(doc.at("/info/title").asText()).isEqualTo("Stock Insight API");
        assertThat(doc.at("/info/description").asText()).contains("현재 데이터 모드: MOCK");
        assertThat(doc.at("/servers/0/url").asText()).isEqualTo("/");
        assertThat(doc.path("paths").size()).isEqualTo(5);
        assertThat(doc.path("paths").has("/api/v1/companies/{stockCode}/sync")).isFalse();
        var search = doc.path("paths").path("/api/v1/companies").path("get");
        assertThat(search.path("operationId").asText()).isEqualTo("searchCompanies");
        assertThat(search.at("/responses/200/content").has("application/json")).isTrue();
        JsonNode limit = null;
        for (var parameter : search.path("parameters")) {
            if (parameter.path("name").asText().equals("limit")) { limit = parameter; }
        }
        assertThat(limit).isNotNull();
        assertThat(limit.at("/schema/minimum").asInt()).isEqualTo(1);
        assertThat(limit.at("/schema/maximum").asInt()).isEqualTo(50);
        assertThat(search.at("/responses/400/$ref").asText()).isEqualTo("#/components/responses/InvalidRequest");
        assertThat(doc.at("/components/responses/InvalidRequest/content/application~1json/example/data").isNull()).isTrue();
        assertThat(doc.at("/components/responses/InvalidRequest/content/application~1json/example/meta/mode").asText()).isEqualTo("MOCK");
    }

    @Test
    void openApiPreservesNumericStringsNullableValuesAndSharedStatuses() {
        var doc = http.getForObject("/v3/api-docs", JsonNode.class);
        for (String property : List.of("AccountValue/value", "MetricValue/value", "PriceValue/closePrice", "PriceValue/marketCap",
            "ShareDisclosure/issuedCommonShares", "ShareDisclosure/treasuryCommonShares",
            "ShareDisclosure/outstandingCommonShares", "ShareDisclosure/preferredShares")) {
            var schema = doc.at("/components/schemas/" + property.replace("/", "/properties/"));
            assertThat(schema.path("type").asText()).as(property).isEqualTo("string");
            assertThat(schema.path("nullable").asBoolean()).as(property + " N/A").isTrue();
            assertThat(schema.path("format").asText()).as(property + " format").isNotEqualTo("int64");
        }
        assertThat(doc.at("/components/schemas/MetricValue/properties/status/enum").toString())
            .contains("AVAILABLE", "NOT_COLLECTED", "NOT_APPLICABLE", "UNSUPPORTED");
        assertThat(doc.at("/components/schemas/MetricValue/properties/reasonCode/enum").toString()).contains("PREFERRED_SHARES", "MISSING_PRICE");
    }

    @Test
    void swaggerUiIsServedLocallyWithTheCorrectDefinitionAndExplicitExecution() {
        var page = http.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        assertThat(page.getBody()).contains("Swagger UI", "swagger-ui-bundle.js");
        var config = http.getForObject("/v3/api-docs/swagger-config", JsonNode.class);
        assertThat(config.path("url").asText()).isEqualTo("/v3/api-docs");
        assertThat(config.path("tryItOutEnabled").asBoolean()).isFalse();
        assertThat(config.path("validatorUrl").asText()).isEmpty();
    }

    @Test
    void fullHttpFlowUsesMysqlFlywayAndPreservesMetadata() {
        var search = http.getForEntity("/api/v1/companies?query=990001", JsonNode.class);
        assertThat(search.getStatusCode().value()).isEqualTo(200);
        assertThat(search.getBody().path("data").get(0).path("stockCode").asText()).isEqualTo("990001");
        var detail = http.getForObject("/api/v1/companies/990001", JsonNode.class);
        assertThat(detail.path("meta").path("mode").asText()).isEqualTo("MOCK");
        assertThat(detail.path("meta").path("market").path("closePriceAsOf").asText()).isEqualTo("2026-11-16");
        assertThat(detail.path("data").path("price").path("closePrice").isTextual()).isTrue();
        var financials = http.getForObject("/api/v1/companies/990001/financials", JsonNode.class);
        assertThat(financials.path("data").path("years").size()).isEqualTo(5);
        assertThat(financials.path("meta").path("financialPeriods").get(0).path("basis").asText()).isEqualTo("CFS");
        assertThat(financials.path("meta").path("financialPeriods").get(0).path("receiptNo").asText()).startsWith("MOCK-");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(2);
        BigDecimal saved = jdbc.queryForObject("""
            SELECT a.amount FROM financial_account a
            JOIN financial_statement s ON s.id = a.statement_id
            JOIN company c ON c.id = s.company_id
            WHERE c.data_mode = 'MOCK' AND c.stock_code = '990001'
              AND s.fiscal_year = 2025 AND s.basis = 'CFS' AND a.standard_code = 'REVENUE'
            """, BigDecimal.class);
        assertThat(saved).isEqualByComparingTo("180000000000");
    }

    @Test
    void partialUnsupportedAndInvalidCasesAreDistinct() {
        var sparse = http.getForObject("/api/v1/companies/990004/financials", JsonNode.class);
        assertThat(sparse.path("data").path("years").size()).isEqualTo(2);
        JsonNode eps = null;
        for (JsonNode account : sparse.path("data").path("years").get(0).path("accounts")) {
            if (account.path("code").asText().equals("BASIC_EPS")) { eps = account; }
        }
        assertThat(eps).isNotNull();
        assertThat(eps.path("value").isNull()).isTrue();
        assertThat(eps.path("status").asText()).isEqualTo("NOT_COLLECTED");
        assertThat(http.getForEntity("/api/v1/companies/990005/financials", JsonNode.class).getStatusCode().value()).isEqualTo(422);
        assertThat(http.getForEntity("/api/v1/companies/000000", JsonNode.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/v1/companies/990001/financials?years=0", JsonNode.class).getStatusCode().value()).isEqualTo(400);
        var wildcard = http.getForObject("/api/v1/companies?query=%25", JsonNode.class);
        assertThat(wildcard.path("data").size()).isZero();
    }

    @Test
    void restartSeedingIsIdempotent() {
        Integer companiesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM company", Integer.class);
        Integer historyBefore = jdbc.queryForObject("SELECT COUNT(*) FROM data_sync_history", Integer.class);
        seed.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company", Integer.class)).isEqualTo(companiesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_sync_history", Integer.class)).isEqualTo(historyBefore);
    }

    @Test
    void metricsUseAnnualCfsAndReturnDecimalStringsWithAuditMetadata() {
        var response = http.getForEntity("/api/v1/companies/990001/metrics", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = response.getBody();
        assertThat(body.path("data").path("fiscalYear").asInt()).isEqualTo(2025);
        assertThat(body.path("data").path("basis").asText()).isEqualTo("CFS");
        assertThat(body.path("data").path("metrics").size()).isEqualTo(10);
        assertMetric(body, "REVENUE_GROWTH", "12.5");
        assertMetric(body, "OPERATING_MARGIN", "10");
        assertMetric(body, "NET_MARGIN", "6.666667");
        assertMetric(body, "ROE", "11");
        assertMetric(body, "ROA", "4");
        assertMetric(body, "DEBT_RATIO", "150");
        assertMetric(body, "EPS", "1100");
        assertMetric(body, "BPS", "10526.315789");
        assertMetric(body, "PER", "29.090909");
        assertMetric(body, "PBR", "3.04");
        assertThat(body.path("meta").path("financialPeriods").size()).isEqualTo(2);
        assertThat(body.path("meta").path("financialPeriods").get(0).path("receiptNo").asText())
            .isEqualTo("MOCK-990001-2025-CFS");
        assertThat(metric(body, "PER").path("priceAsOf").asText()).isEqualTo("2026-11-16");
        assertThat(body.path("data").path("shares").path("outstandingCommonShares").isTextual()).isTrue();
    }

    @Test
    void preferredLossAndBasisChangeHaveExplicitNaReasons() {
        var preferred = http.getForObject("/api/v1/companies/990003/metrics", JsonNode.class);
        assertNa(preferred, "BPS", "NOT_APPLICABLE", "PREFERRED_SHARES");
        assertNa(preferred, "PBR", "NOT_APPLICABLE", "PREFERRED_SHARES");
        assertMetric(preferred, "ROE", "11");
        var loss = http.getForObject("/api/v1/companies/990002/metrics", JsonNode.class);
        assertMetric(loss, "EPS", "-600");
        assertMetric(loss, "ROE", "-6");
        assertNa(loss, "PER", "NOT_APPLICABLE", "NON_POSITIVE_EPS");
        var sparse = http.getForObject("/api/v1/companies/990004/metrics", JsonNode.class);
        assertNa(sparse, "REVENUE_GROWTH", "NOT_APPLICABLE", "BASIS_MISMATCH");
        assertNa(sparse, "EPS", "NOT_COLLECTED", "ACCOUNT_NOT_AVAILABLE");
        assertNa(sparse, "PER", "NOT_COLLECTED", "MISSING_PRICE");
        assertNa(sparse, "PBR", "NOT_COLLECTED", "MISSING_PRICE");
        assertMetric(sparse, "ROA", "4");
        assertThat(http.getForEntity("/api/v1/companies/990005/metrics", JsonNode.class).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void repeatedMetricQueriesDoNotPersistComputedResults() {
        Long accounts = jdbc.queryForObject("SELECT COUNT(*) FROM financial_account", Long.class);
        Long history = jdbc.queryForObject("SELECT COUNT(*) FROM data_sync_history", Long.class);
        for (int i = 0; i < 3; i++) {
            assertThat(http.getForEntity("/api/v1/companies/990001/metrics", JsonNode.class).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_account", Long.class)).isEqualTo(accounts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_sync_history", Long.class)).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE '%metric%'", Integer.class)).isZero();
    }

    private JsonNode metric(JsonNode body, String code) {
        for (JsonNode value : body.path("data").path("metrics")) {
            if (value.path("code").asText().equals(code)) { return value; }
        }
        throw new AssertionError("Missing metric: " + code);
    }

    @Test
    void annualProfitabilityHasFiveYearsAndPreservesMissingAndBasisChanges() {
        var normal = http.getForObject("/api/v1/companies/990001/metrics/profitability", JsonNode.class);
        assertThat(normal.path("data").path("years").size()).isEqualTo(5);
        var latest = normal.path("data").path("years").get(0);
        assertThat(latest.path("metrics").size()).isEqualTo(2);
        assertThat(latest.path("metrics").get(0).path("value").asText()).isEqualTo("10.000000");
        assertThat(latest.path("metrics").get(1).path("value").asText()).isEqualTo("6.666667");
        assertThat(normal.path("data").path("years").get(4).path("metrics").get(0).path("value").asText()).isEqualTo("18.000000");
        var sparse = http.getForObject("/api/v1/companies/990004/metrics/profitability", JsonNode.class);
        assertThat(sparse.path("data").path("years").get(0).path("basis").asText()).isEqualTo("CFS");
        assertThat(sparse.path("data").path("years").get(1).path("basis").asText()).isEqualTo("OFS");
        assertThat(sparse.path("data").path("years").get(0).path("metrics").get(0).path("value").isNull()).isTrue();
        assertThat(http.getForEntity("/api/v1/companies/990001/metrics/profitability?years=6", JsonNode.class).getStatusCode().value()).isEqualTo(400);
    }

    private void assertMetric(JsonNode body, String code, String expected) {
        var value = metric(body, code);
        assertThat(value.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(value.path("value").isTextual()).isTrue();
        assertThat(new BigDecimal(value.path("value").asText())).isEqualByComparingTo(expected);
    }

    private void assertNa(JsonNode body, String code, String status, String reason) {
        var value = metric(body, code);
        assertThat(value.path("value").isNull()).isTrue();
        assertThat(value.path("status").asText()).isEqualTo(status);
        assertThat(value.path("reasonCode").asText()).isEqualTo(reason);
        assertThat(value.path("reason").asText()).isNotBlank();
    }
}
