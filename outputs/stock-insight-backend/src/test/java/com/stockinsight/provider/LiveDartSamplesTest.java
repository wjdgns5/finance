package com.stockinsight.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import com.stockinsight.provider.dart.DartNormalizer;
import com.stockinsight.service.FinancialMetricCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.api.MetricModels.*;
import static com.stockinsight.domain.DataTypes.*;
import static com.stockinsight.provider.dart.DartModels.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveDartSamplesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DartNormalizer normalizer = new DartNormalizer(mapper);
    private final Map<String, FinancialStatement> normalized = new HashMap<>();
    private final Instant collected = Instant.parse("2026-11-17T06:10:00Z");

    @ParameterizedTest(name = "{0} / {1}")
    @CsvFileSource(resources = "/dart-live/normalization-expectations.csv", numLinesToSkip = 1)
    void normalizesActualResponseToIndependentlyRecordedAccountAmounts(String stock, StandardAccount account, String expected) throws Exception {
        var statement = statement(stock, 2025);
        var actual = statement.getAccounts().stream().filter(a -> a.getCode() == account).findFirst();
        if (expected == null) {
            assertThat(actual).isEmpty();
            assertThat(statement.getNormalizationNotes()).contains("EPS_COMMON_SCOPE_UNCONFIRMED");
        } else {
            assertThat(actual).isPresent();
            assertThat(actual.get().getAmount()).isEqualByComparingTo(expected);
            assertThat(actual.get().getOriginalAccountId()).isNotBlank();
            assertThat(actual.get().getOriginalAmount()).isNotBlank();
        }
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvFileSource(resources = "/dart-live/metric-expectations.csv", numLinesToSkip = 1)
    void actualInputsMatchIndependentDecimalExpectations(String stock, MetricCode code, String expected,
                                                         DataStatus status, MetricReason reason) throws Exception {
        var current = statement(stock, 2025);
        var previous = statement(stock, 2024);
        var preferred = current.getPreferredShares() == null ? null : current.getPreferredShares() > 0;
        String close = switch (stock) { case "000270" -> "95000"; case "011170" -> "45600"; default -> "196800"; };
        var detail = new CompanyDetail(stock, current.getCompany().getCorpCode(), stock, Market.KOSPI,
            "C", "검증", 12, preferred, true, new PriceValue(new BigDecimal(close), null, "KRW", DataStatus.AVAILABLE, null));
        var price = new PriceMeta(LocalDate.of(2026, 11, 16), DataSource.FSC, null, null, collected, "COMMON_STOCK");
        var actual = new FinancialMetricCalculator().calculate(detail, year(current), year(previous),
            period(current), period(previous), price).stream().filter(m -> m.code() == code).findFirst().orElseThrow();
        assertThat(actual.status()).isEqualTo(status);
        assertThat(actual.reasonCode()).isEqualTo(reason);
        if (expected == null) { assertThat(actual.value()).isNull(); }
        else { assertThat(actual.value()).isEqualByComparingTo(expected); }
    }

    @Test
    void actualSamsungIncomeAndComprehensiveIncomeAreSameStandardAccount() throws Exception {
        var result = statement("005930", 2025);
        assertThat(result.getAccounts().stream().filter(a -> a.getCode() == StandardAccount.NET_INCOME)).hasSize(1);
        assertThat(result.getRawPayload()).contains("\"sj_div\":\"IS\"", "\"sj_div\":\"CIS\"");
        assertThat(result.getNormalizationNotes()).doesNotContain("AMBIGUOUS_ACCOUNT:NET_INCOME");
        assertThat(result.getPreferredShares()).isEqualTo(815974664L);
        assertThat(result.getOutstandingCommonShares()).isEqualTo(5827808935L);
    }

    @Test
    void conflictingIncomeAndComprehensiveIncomeRemainUnmapped() throws Exception {
        var raw = raw("005930", 2025);
        for (JsonNode row : raw.path("financials").path("list")) {
            if ("CIS".equals(row.path("sj_div").asText()) && "ifrs-full_ProfitLoss".equals(row.path("account_id").asText())) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) row).put("thstrm_amount", "1");
            }
        }
        var result = normalize("005930", 2025, raw);
        assertThat(result.getAccounts()).noneMatch(a -> a.getCode() == StandardAccount.NET_INCOME);
        assertThat(result.getNormalizationNotes()).contains("AMBIGUOUS_ACCOUNT:NET_INCOME");
    }

    private FinancialStatement statement(String stock, int year) throws Exception {
        String key = stock + ":" + year;
        if (!normalized.containsKey(key)) { normalized.put(key, normalize(stock, year, raw(stock, year))); }
        return normalized.get(key);
    }

    private JsonNode raw(String stock, int year) throws Exception {
        try (var input = getClass().getResourceAsStream("/dart-live/" + stock + "-" + year + ".json")) {
            if (input == null) { throw new IllegalArgumentException("Missing captured fixture"); }
            return mapper.readTree(input);
        }
    }

    private FinancialStatement normalize(String stock, int year, JsonNode raw) throws Exception {
        var financials = raw.path("financials");
        String corp = financials.path("list").get(0).path("corp_code").asText();
        var company = new Company(stock, corp, stock, Market.KOSPI, "C", "검증", 12, null, true,
            DataMode.REAL, DataSource.DART, collected);
        return normalizer.normalize(company, year, StatementBasis.CFS, mapper.treeToValue(raw.path("filing"), Filing.class),
            new FinancialResponse(mapper.readerForListOf(FinancialRow.class).readValue(financials.path("list")), financials),
            new PeriodResponse(mapper.readerForListOf(PeriodRow.class).readValue(raw.path("periods").path("list")), raw.path("periods")),
            new SharesResponse(mapper.readerForListOf(ShareRow.class).readValue(raw.path("shares").path("list")), raw.path("shares")), collected);
    }

    private FinancialYear year(FinancialStatement statement) {
        var accounts = Arrays.stream(StandardAccount.values()).map(code -> {
            var account = statement.getAccounts().stream().filter(a -> a.getCode() == code).findFirst();
            return new AccountValue(code, code.label(), account.map(a -> a.getAmount()).orElse(null), code.unit(),
                account.isPresent() ? DataStatus.AVAILABLE : DataStatus.NOT_COLLECTED, null, null);
        }).toList();
        return new FinancialYear(statement.getFiscalYear(), statement.getBasis(), "KRW", accounts,
            new ShareDisclosure(statement.getIssuedCommonShares(), statement.getTreasuryCommonShares(),
                statement.getOutstandingCommonShares(), statement.getPreferredShares(), statement.getSharesAsOf(), statement.getSharesReceiptNo()));
    }

    private StatementMeta period(FinancialStatement statement) {
        return new StatementMeta(statement.getFiscalYear(), "11011", statement.getBasis(), statement.getReceiptNo(),
            statement.getFiledAt(), statement.getPeriodStart(), statement.getPeriodEnd(), DataSource.DART, collected);
    }
}
