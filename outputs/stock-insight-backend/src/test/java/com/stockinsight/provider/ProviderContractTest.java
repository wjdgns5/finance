package com.stockinsight.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import com.stockinsight.provider.dart.*;
import com.stockinsight.provider.fsc.FscMarketDataProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.stockinsight.domain.DataTypes.*;
import static com.stockinsight.provider.dart.DartModels.*;
import static org.assertj.core.api.Assertions.*;

class ProviderContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T02:00:00Z"), ZoneOffset.UTC);
    private final StubProviders stub = new StubProviders();
    private final DartClient client = new DartClient(mapper, clock, stub.base() + "/dart", "test-secret-not-real");
    private final DartNormalizer normalizer = new DartNormalizer(mapper);
    private final DartFinancialDataProvider provider = new DartFinancialDataProvider(client, normalizer, clock);
    @AfterEach void close() { stub.close(); }

    @Test
    void directoryIsZippedListedOnlyAndReused() {
        assertThat(client.directory()).hasSize(1);
        assertThat(client.directory().getFirst().stockCode()).isEqualTo("123456");
        assertThat(stub.calls("/dart/corpCode.xml")).isEqualTo(1);
        var company = provider.company("123456").orElseThrow();
        assertThat(company.getDataMode()).isEqualTo(DataMode.REAL);
        assertThat(company.getHasPreferredShares()).isNull();
        assertThat(company.getName()).isEqualTo("계약검증제조");
    }

    @Test
    void rejectsXmlEntitiesAndDuplicateStockCodes() {
        String xml = "<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///never-read'>]><result>&ext;</result>";
        assertThatThrownBy(() -> DartClient.parseDirectory(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(ProviderException.class);
        String row = "<list><corp_code>00123456</corp_code><corp_name>가상</corp_name><stock_code>123456</stock_code></list>";
        assertThatThrownBy(() -> DartClient.parseDirectory(("<result>" + row + row + "</result>").getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ProviderException.class);
    }

    @Test
    void normalizesCurrentAmountsWithOriginalsAndUnmappedWarnings() {
        FinancialStatement result = collect();
        assertThat(result.getFiscalYear()).isEqualTo(2025);
        assertThat(result.getReceiptNo()).isEqualTo("20260320000002");
        assertThat(result.getFiledAt()).hasToString("2026-03-20");
        assertThat(result.getPeriodStart()).hasToString("2025-01-01");
        assertThat(result.getAccounts()).hasSize(12);
        assertThat(amount(result, StandardAccount.REVENUE)).isEqualByComparingTo("1200");
        assertThat(amount(result, StandardAccount.INVESTING_CASH_FLOW)).isEqualByComparingTo("-150");
        assertThat(amount(result, StandardAccount.FINANCING_CASH_FLOW)).isEqualByComparingTo("0");
        assertThat(result.getAccounts().stream().filter(a -> a.getCode() == StandardAccount.REVENUE).findFirst().orElseThrow().getOriginalAmount()).isEqualTo("1,200");
        assertThat(result.getNormalizationNotes()).contains("UNMAPPED_ACCOUNT");
        assertThat(result.getRawPayload()).contains("999,999", "기업고유계정").doesNotContain("test-secret-not-real", "crtfc_key");
        assertThat(result.getOutstandingCommonShares()).isEqualTo(90);
        assertThat(result.getPreferredShares()).isZero();
    }

    @Test
    void ofsFallbackOnlyHappensForNoDataNotRateLimit() {
        stub.onlyOfs = true;
        assertThat(collect().getBasis()).isEqualTo(StatementBasis.OFS);
        assertThat(stub.calls("/dart/fnlttSinglAcntAll.json")).isEqualTo(2);
        stub.reset();
        stub.rateLimited = true;
        assertThatThrownBy(this::collect).isInstanceOfSatisfying(ProviderException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("DART_RATE_LIMIT"));
        assertThat(stub.calls("/dart/fnlttSinglAcntAll.json")).isEqualTo(1);
    }

    @Test
    void mismatchedCorrectionAndMissingPeriodAreRejected() {
        stub.overrides.put("/dart/dart-unused", "{}");
        stub.overrides.put("/dart/fnlttSinglAcntAll.json", StubProviders.fixture("dart-financials.json").replace("20260320000002", "20260318000001"));
        assertThatThrownBy(this::collect).isInstanceOfSatisfying(ProviderException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("DART_REVISION_PENDING"));
        stub.reset();
        stub.overrides.put("/dart/fnlttSinglAcnt.json", StubProviders.NO_DATA);
        assertThatThrownBy(this::collect).isInstanceOfSatisfying(ProviderException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("DART_PERIOD_UNCONFIRMED"));
    }

    @Test
    void ambiguousAndInvalidAccountsStayMissing() throws Exception {
        var raw = mapper.readTree(StubProviders.fixture("dart-financials.json"));
        var rows = (com.fasterxml.jackson.databind.node.ArrayNode) raw.path("list");
        rows.add(rows.get(0).deepCopy());
        ((com.fasterxml.jackson.databind.node.ObjectNode) rows.get(1)).put("thstrm_amount", "1,2,00");
        stub.overrides.put("/dart/fnlttSinglAcntAll.json", raw.toString());
        var result = collect();
        assertThat(result.getAccounts()).noneMatch(a -> a.getCode() == StandardAccount.REVENUE || a.getCode() == StandardAccount.OPERATING_PROFIT);
        assertThat(result.getNormalizationNotes()).contains("AMBIGUOUS_ACCOUNT:REVENUE", "INVALID_AMOUNT:OPERATING_PROFIT");
    }

    @Test
    void preferredCompanyNeedsExplicitCommonEpsAndNeverAssumesZeroPreferred() {
        String shares = StubProviders.fixture("dart-shares.json").replace("\"istc_totqy\":\"0\"", "\"istc_totqy\":\"1\"");
        stub.overrides.put("/dart/stockTotqySttus.json", shares);
        var preferred = collect();
        assertThat(preferred.getPreferredShares()).isEqualTo(1);
        assertThat(preferred.getAccounts()).noneMatch(a -> a.getCode() == StandardAccount.BASIC_EPS);
        assertThat(preferred.getNormalizationNotes()).contains("EPS_COMMON_SCOPE_UNCONFIRMED");
        stub.overrides.put("/dart/fnlttSinglAcntAll.json", StubProviders.fixture("dart-financials.json").replace("기본주당이익", "기본주당이익(보통주)"));
        assertThat(amount(collect(), StandardAccount.BASIC_EPS)).isEqualByComparingTo("30");
        stub.overrides.put("/dart/stockTotqySttus.json", StubProviders.NO_DATA);
        assertThat(collect().getPreferredShares()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"010", "011", "012", "020", "800", "901"})
    void statusErrorsAreSanitized(String code) {
        stub.overrides.put("/dart/fnlttSinglAcntAll.json", "{\"status\":\"" + code + "\",\"message\":\"test-secret-not-real crtfc_key leaked\"}");
        assertThatThrownBy(() -> client.financials("00123456", 2025, "CFS")).isInstanceOfSatisfying(ProviderException.class,
            ex -> { assertThat(ex.getMessage()).doesNotContain("test-secret-not-real", "crtfc_key", "leaked"); assertThat(ex.getCause()).isNull(); });
    }

    @Test
    void missingKeysDoNotMakeNetworkRequests() {
        var missing = new DartClient(mapper, clock, stub.base(), "");
        assertThatThrownBy(missing::directory).isInstanceOfSatisfying(ProviderException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("DART_KEY_MISSING"));
        assertThat(stub.calls("/corpCode.xml")).isZero();
        var fsc = new FscMarketDataProvider(mapper, clock, stub.base(), "/price", "", new ExternalHttp());
        assertThatThrownBy(() -> fsc.latestPrice(company())).isInstanceOfSatisfying(ProviderException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("FSC_KEY_MISSING"));
    }

    @Test
    void fscChoosesLatestExactStockAndRetainsEachDateAndSource() {
        var price = fsc().latestPrice(company()).orElseThrow();
        assertThat(price.getClosePrice()).isEqualByComparingTo("90");
        assertThat(price.getMarketCap()).isEqualByComparingTo("9000");
        assertThat(price.getClosePriceAsOf()).hasToString("2026-11-16");
        assertThat(price.getMarketCapAsOf()).isEqualTo(price.getClosePriceAsOf());
        assertThat(price.getClosePriceSource()).isEqualTo(DataSource.FSC);
        stub.noPrice = true;
        assertThat(fsc().latestPrice(company())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void rejectsInvalidClose(String close) {
        stub.overrides.put("/fsc/getStockPriceInfo_V2", StubProviders.fixture("fsc-prices.json").replace("\"90\"", "\"" + close + "\""));
        assertThatThrownBy(() -> fsc().latestPrice(company())).isInstanceOf(ProviderException.class);
    }

    @Test
    void acceptsSingleFscItemAndMissingMarketCap() throws Exception {
        var raw = mapper.readTree(StubProviders.fixture("fsc-prices.json"));
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) raw.path("response").path("body");
        var row = (com.fasterxml.jackson.databind.node.ObjectNode) body.path("items").path("item").get(1);
        row.remove("mrktTotAmt"); body.put("totalCount", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("items")).set("item", row);
        stub.overrides.put("/fsc/getStockPriceInfo_V2", raw.toString());
        assertThat(fsc().latestPrice(company()).orElseThrow().getMarketCap()).isNull();
    }

    @Test
    void eachOfFiveYearsUsesItsOwnAnnualCurrentAmount() throws Exception {
        var remote = org.mockito.Mockito.mock(DartClient.class);
        var c = company();
        var filings = new java.util.ArrayList<Filing>();
        for (int year = 2021; year <= 2025; year++) {
            String receipt = (year + 1) + "0320000002";
            filings.add(new Filing(receipt, (year + 1) + "0320", "사업보고서 (" + year + ".12)", c.getCorpCode(), ""));
            var financial = mapper.readTree(StubProviders.fixture("dart-financials.json").replace("2025", String.valueOf(year)).replace("20260320000002", receipt));
            var period = mapper.readTree(StubProviders.fixture("dart-periods.json").replace("2025", String.valueOf(year)).replace("20260320000002", receipt));
            var shares = mapper.readTree(StubProviders.fixture("dart-shares.json").replace("2025", String.valueOf(year)).replace("20260320000002", receipt));
            org.mockito.Mockito.when(remote.financials(c.getCorpCode(), year, "CFS")).thenReturn(new FinancialResponse(
                mapper.readerForListOf(FinancialRow.class).readValue(financial.path("list")), financial));
            org.mockito.Mockito.when(remote.periods(c.getCorpCode(), year)).thenReturn(new PeriodResponse(
                mapper.readerForListOf(PeriodRow.class).readValue(period.path("list")), period));
            org.mockito.Mockito.when(remote.shares(c.getCorpCode(), year)).thenReturn(new SharesResponse(
                mapper.readerForListOf(ShareRow.class).readValue(shares.path("list")), shares));
        }
        org.mockito.Mockito.when(remote.annualFilings(c.getCorpCode(), 2015)).thenReturn(filings);
        var years = new DartFinancialDataProvider(remote, normalizer, clock).financialStatements(c);
        assertThat(years).extracting(FinancialStatement::getFiscalYear).containsExactly(2025, 2024, 2023, 2022, 2021);
        assertThat(years).allSatisfy(s -> assertThat(amount(s, StandardAccount.REVENUE)).isEqualByComparingTo("1200"));
    }

    @Test
    void beforeAnnualFilingUsesFiveYearsEndingAtLatestPublishedYear() throws Exception {
        var earlier = Clock.fixed(Instant.parse("2026-02-01T02:00:00Z"), ZoneOffset.UTC);
        var remote = org.mockito.Mockito.mock(DartClient.class);
        var c = company();
        var filings = new java.util.ArrayList<Filing>();
        for (int year = 2020; year <= 2024; year++) {
            String receipt = (year + 1) + "0320000002";
            filings.add(new Filing(receipt, (year + 1) + "0320", "사업보고서 (" + year + ".12)", c.getCorpCode(), ""));
            var financial = mapper.readTree(StubProviders.fixture("dart-financials.json").replace("2025", String.valueOf(year)).replace("20260320000002", receipt));
            var period = mapper.readTree(StubProviders.fixture("dart-periods.json").replace("2025", String.valueOf(year)).replace("20260320000002", receipt));
            var shares = mapper.readTree(StubProviders.fixture("dart-shares.json").replace("2025", String.valueOf(year)).replace("20260320000002", receipt));
            org.mockito.Mockito.when(remote.financials(c.getCorpCode(), year, "CFS")).thenReturn(new FinancialResponse(
                mapper.readerForListOf(FinancialRow.class).readValue(financial.path("list")), financial));
            org.mockito.Mockito.when(remote.periods(c.getCorpCode(), year)).thenReturn(new PeriodResponse(
                mapper.readerForListOf(PeriodRow.class).readValue(period.path("list")), period));
            org.mockito.Mockito.when(remote.shares(c.getCorpCode(), year)).thenReturn(new SharesResponse(
                mapper.readerForListOf(ShareRow.class).readValue(shares.path("list")), shares));
        }
        org.mockito.Mockito.when(remote.annualFilings(c.getCorpCode(), 2015)).thenReturn(filings);
        var years = new DartFinancialDataProvider(remote, normalizer, earlier).financialStatements(c);
        assertThat(years).extracting(FinancialStatement::getFiscalYear).containsExactly(2024, 2023, 2022, 2021, 2020);
    }

    @Test
    void financialIndustryIsMarkedUnsupportedFromDisclosedCode() {
        stub.overrides.put("/dart/company.json", StubProviders.fixture("dart-profile.json").replace("26110", "64110"));
        var finance = company();
        assertThat(finance.isSupported()).isFalse();
        assertThat(provider.financialStatements(finance)).isEmpty();
        assertThat(stub.calls("/dart/fnlttSinglAcntAll.json")).isZero();
    }

    private FinancialStatement collect() { return provider.financialStatements(company()).getFirst(); }
    private Company company() { return provider.company("123456").orElseThrow(); }
    private FscMarketDataProvider fsc() {
        return new FscMarketDataProvider(mapper, clock, stub.base() + "/fsc", "/getStockPriceInfo_V2", "test-fsc-key+/=", new ExternalHttp());
    }
    private BigDecimal amount(FinancialStatement statement, StandardAccount code) {
        return statement.getAccounts().stream().filter(a -> a.getCode() == code).findFirst().orElseThrow().getAmount();
    }
}
