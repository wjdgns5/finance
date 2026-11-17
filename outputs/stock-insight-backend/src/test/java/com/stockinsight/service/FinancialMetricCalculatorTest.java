package com.stockinsight.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.api.MetricModels.*;
import static com.stockinsight.domain.DataTypes.*;
import static org.assertj.core.api.Assertions.*;

class FinancialMetricCalculatorTest {
    private final FinancialMetricCalculator calculator = new FinancialMetricCalculator();

    @ParameterizedTest(name = "{0} / {1}")
    @CsvFileSource(resources = "/metric-expectations.csv", numLinesToSkip = 1)
    void matchesIndependentlyPreparedExpectations(String scenario, MetricCode code,
                                                DataStatus status, String expected, MetricReason reason) {
        Fixture f = new Fixture();
        StandardAccount numerator = switch (code) {
            case REVENUE_GROWTH -> StandardAccount.REVENUE;
            case OPERATING_MARGIN -> StandardAccount.OPERATING_PROFIT;
            case NET_MARGIN, ROA -> StandardAccount.NET_INCOME;
            case ROE -> StandardAccount.PARENT_NET_INCOME;
            case DEBT_RATIO -> StandardAccount.TOTAL_LIABILITIES;
            case EPS, PER -> StandardAccount.BASIC_EPS;
            case BPS, PBR -> StandardAccount.PARENT_EQUITY;
        };
        switch (scenario) {
            case "NORMAL" -> { }
            case "NEGATIVE" -> f.amount(numerator, f.accounts.get(numerator).value().negate());
            case "ZERO" -> f.amount(numerator, BigDecimal.ZERO);
            case "NULL" -> f.amount(numerator, null);
            case "INSUFFICIENT" -> {
                switch (code) {
                    case REVENUE_GROWTH -> f.previous = null;
                    case EPS -> f.accounts.put(numerator, new AccountValue(numerator, numerator.label(),
                        new BigDecimal("30"), numerator.unit(), DataStatus.NOT_COLLECTED, "수집 중", null));
                    case BPS -> f.shares = new ShareDisclosure(100L, 10L, null, 0L, f.end, "SHARES-2025");
                    case PER, PBR -> f.price = new PriceValue(null, null, "KRW", DataStatus.NOT_COLLECTED, "없음");
                    default -> f.accounts.remove(numerator);
                }
            }
            case "INVALID" -> {
                switch (code) {
                    case REVENUE_GROWTH -> {
                        f.previous = new FinancialYear(2024, StatementBasis.OFS, "KRW", f.previous.accounts(), null);
                        f.previousPeriod = Fixture.period(2024, StatementBasis.OFS);
                    }
                    case OPERATING_MARGIN, NET_MARGIN -> f.amount(StandardAccount.REVENUE, BigDecimal.ZERO);
                    case ROE -> f.amount(StandardAccount.PARENT_EQUITY, BigDecimal.ZERO);
                    case ROA -> f.amount(StandardAccount.TOTAL_ASSETS, BigDecimal.ZERO);
                    case DEBT_RATIO -> f.amount(StandardAccount.TOTAL_EQUITY, BigDecimal.ZERO);
                    case EPS -> f.accounts.put(numerator, new AccountValue(numerator, numerator.label(),
                        new BigDecimal("30"), AccountUnit.KRW, DataStatus.AVAILABLE, null, null));
                    case BPS -> f.shares = new ShareDisclosure(100L, 10L, 0L, 0L, f.end, "SHARES-2025");
                    case PER, PBR -> f.price = new PriceValue(new BigDecimal("-90"), null, "KRW", DataStatus.AVAILABLE, null);
                }
            }
            default -> throw new AssertionError("Unknown scenario: " + scenario);
        }
        MetricValue actual = f.metric(code);
        assertThat(actual.status()).isEqualTo(status);
        assertThat(actual.reasonCode()).isEqualTo(reason);
        if (expected == null) {
            assertThat(actual.value()).isNull();
            assertThat(actual.reason()).isNotBlank();
        } else {
            assertThat(actual.value()).isEqualByComparingTo(expected);
            assertThat(actual.reason()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
        REVENUE_GROWTH, REVENUE, NON_POSITIVE_PREVIOUS_REVENUE
        OPERATING_MARGIN, REVENUE, NON_POSITIVE_REVENUE
        NET_MARGIN, REVENUE, NON_POSITIVE_REVENUE
        ROE, PARENT_EQUITY, NON_POSITIVE_PARENT_EQUITY
        ROA, TOTAL_ASSETS, NON_POSITIVE_ASSETS
        DEBT_RATIO, TOTAL_EQUITY, NON_POSITIVE_EQUITY
        PER, BASIC_EPS, NON_POSITIVE_EPS
        PBR, PARENT_EQUITY, NON_POSITIVE_BPS
        """)
    void rejectsEveryNonPositiveFinancialDenominator(MetricCode code, StandardAccount account, MetricReason reason) {
        for (String value : List.of("0", "-1")) {
            Fixture f = new Fixture();
            if (code == MetricCode.REVENUE_GROWTH) {
                f.previous = new FinancialYear(2024, StatementBasis.CFS, "KRW",
                    List.of(Fixture.account(account, new BigDecimal(value))), null);
            } else {
                f.amount(account, new BigDecimal(value));
            }
            assertThat(f.metric(code).reasonCode()).isEqualTo(reason);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveShareDenominator(String value) {
        Fixture f = new Fixture();
        f.shares = new ShareDisclosure(null, null, Long.valueOf(value), 0L, f.end, "S");
        assertThat(f.metric(MetricCode.BPS).reasonCode()).isEqualTo(MetricReason.INVALID_SHARES);
        assertThat(f.metric(MetricCode.PBR).reasonCode()).isEqualTo(MetricReason.INVALID_SHARES);
    }

    @Test
    void preferredSharesAlwaysBlockBpsAndPbrWithoutBlockingOtherMetrics() {
        Fixture f = new Fixture();
        f.preferred = true;
        f.assertReason(MetricReason.PREFERRED_SHARES, MetricCode.BPS, MetricCode.PBR);
        assertThat(f.metric(MetricCode.ROE).value()).isEqualByComparingTo("15");
        assertThat(f.metric(MetricCode.PER).value()).isEqualByComparingTo("3");
        f.preferred = false;
        f.shares = new ShareDisclosure(100L, 10L, 90L, 1L, f.end, "S");
        f.assertReason(MetricReason.PREFERRED_SHARES, MetricCode.BPS, MetricCode.PBR);
    }

    @Test
    void unknownPreferredStatusNeverMeansNoPreferredShares() {
        Fixture f = new Fixture();
        f.preferred = null;
        f.assertReason(MetricReason.UNKNOWN_PREFERRED_SHARES, MetricCode.BPS, MetricCode.PBR);
        f.preferred = false;
        f.shares = new ShareDisclosure(100L, 10L, 90L, null, f.end, "S");
        f.assertReason(MetricReason.UNKNOWN_PREFERRED_SHARES, MetricCode.BPS, MetricCode.PBR);
    }

    @Test
    void usesParentEquityForCfsAndTotalEquityForOfs() {
        Fixture f = new Fixture();
        assertThat(f.metric(MetricCode.BPS).value()).isEqualByComparingTo("6.666667");
        f.basis = StatementBasis.OFS;
        f.period = Fixture.period(2025, f.basis);
        assertThat(f.metric(MetricCode.BPS).value()).isEqualByComparingTo("8.888889");
        assertThat(f.metric(MetricCode.PBR).value()).isEqualByComparingTo("10.125");
        assertThat(f.metric(MetricCode.BPS).usedAccounts()).containsExactly(StandardAccount.TOTAL_EQUITY);
        f.accounts.remove(StandardAccount.PARENT_EQUITY);
        f.accounts.remove(StandardAccount.PARENT_NET_INCOME);
        assertThat(f.metric(MetricCode.BPS).status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(f.metric(MetricCode.ROE).status()).isEqualTo(DataStatus.NOT_COLLECTED);
    }

    @Test
    void checksShareDateSourceAndArithmetic() {
        Fixture f = new Fixture();
        f.shares = new ShareDisclosure(100L, 10L, 90L, 0L, f.end.minusDays(1), "S");
        f.assertReason(MetricReason.SHARE_DATE_MISMATCH, MetricCode.BPS, MetricCode.PBR);
        f.shares = new ShareDisclosure(100L, 10L, 91L, 0L, f.end, "S");
        f.assertReason(MetricReason.INVALID_SHARES, MetricCode.BPS, MetricCode.PBR);
        f.shares = new ShareDisclosure(100L, 10L, 90L, 0L, f.end, null);
        f.assertReason(MetricReason.MISSING_SHARES, MetricCode.BPS, MetricCode.PBR);
    }

    @Test
    void doesNotUseRoundedBpsOrEpsForAnotherCalculation() {
        Fixture f = new Fixture();
        f.amount(StandardAccount.PARENT_EQUITY, BigDecimal.ONE);
        f.shares = new ShareDisclosure(3L, 0L, 3L, 0L, f.end, "S");
        f.price = new PriceValue(BigDecimal.ONE, null, "KRW", DataStatus.AVAILABLE, null);
        assertThat(f.metric(MetricCode.BPS).value()).isEqualByComparingTo("0.333333");
        assertThat(f.metric(MetricCode.PBR).value()).isEqualByComparingTo("3");
        f.amount(StandardAccount.BASIC_EPS, new BigDecimal("1.00000049"));
        assertThat(f.metric(MetricCode.EPS).value()).isEqualTo(new BigDecimal("1.00000049"));
        // 1 / 1.00000049 = 0.999999510..., 최종 여섯 자리 반올림은 1.000000입니다.
        assertThat(f.metric(MetricCode.PER).value()).isEqualByComparingTo("1");
        f.amount(StandardAccount.PARENT_EQUITY, new BigDecimal("0.0000001"));
        assertThat(f.metric(MetricCode.BPS).value()).isEqualByComparingTo("0");
        assertThat(f.metric(MetricCode.PBR).value()).isEqualByComparingTo("30000000");
    }

    @Test
    void priceAbsenceOnlyAffectsPriceDependentMetrics() {
        Fixture f = new Fixture();
        f.price = null;
        f.assertReason(MetricReason.MISSING_PRICE, MetricCode.PER, MetricCode.PBR);
        assertThat(f.values().stream().filter(v -> v.status() == DataStatus.AVAILABLE)).hasSize(8);
        f.price = new PriceValue(new BigDecimal("90"), null, "KRW", DataStatus.AVAILABLE, null);
        f.priceMeta = null;
        f.assertReason(MetricReason.MISSING_PRICE, MetricCode.PER, MetricCode.PBR);
    }

    @Test
    void growthNeverSkipsAMissingYearAndBasisChangeDoesNotBlockOtherMetrics() {
        Fixture f = new Fixture();
        f.previous = new FinancialYear(2023, StatementBasis.CFS, "KRW", f.previous.accounts(), null);
        assertThat(f.metric(MetricCode.REVENUE_GROWTH).reasonCode()).isEqualTo(MetricReason.MISSING_PREVIOUS_YEAR);
        f.previous = new FinancialYear(2024, StatementBasis.OFS, "KRW", f.previous.accounts(), null);
        f.previousPeriod = Fixture.period(2024, StatementBasis.OFS);
        assertThat(f.metric(MetricCode.REVENUE_GROWTH).reasonCode()).isEqualTo(MetricReason.BASIS_MISMATCH);
        assertThat(f.values().stream().filter(v -> v.status() == DataStatus.AVAILABLE)).hasSize(9);
    }

    @Test
    void missingOrInvalidAnnualContextIsNeverSilentlyAccepted() {
        Fixture f = new Fixture();
        f.period = null;
        assertThat(f.values()).allSatisfy(v -> assertThat(v.reasonCode()).isEqualTo(MetricReason.MISSING_METADATA));
        f.period = Fixture.period(2024, StatementBasis.CFS);
        assertThat(f.values()).allSatisfy(v -> assertThat(v.reasonCode()).isEqualTo(MetricReason.INVALID_PERIOD));
        f.period = Fixture.period(2025, StatementBasis.CFS);
        f.currency = "USD";
        assertThat(f.values()).allSatisfy(v -> assertThat(v.reasonCode()).isEqualTo(MetricReason.INVALID_CURRENCY));
    }

    @Test
    void duplicateAccountIsRejectedWithoutChoosingAnArbitraryValue() {
        Fixture f = new Fixture();
        f.extra = Fixture.account(StandardAccount.BASIC_EPS, new BigDecimal("999"));
        f.assertReason(MetricReason.DUPLICATE_ACCOUNT, MetricCode.EPS, MetricCode.PER);
        assertThat(f.metric(MetricCode.ROE).status()).isEqualTo(DataStatus.AVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(MetricCode.class)
    void eachMetricHasExplanationAndMachineReadableUnit(MetricCode code) {
        MetricValue result = new Fixture().metric(code);
        assertThat(result.formula()).isNotBlank();
        assertThat(result.explanation()).isNotBlank();
        assertThat(result.naConditions()).isNotBlank();
        assertThat(result.usedAccounts()).isNotEmpty();
        assertThat(result.unit()).isNotNull();
        assertThat(result.basisNote()).isNotBlank();
        if (code == MetricCode.PER || code == MetricCode.PBR) {
            assertThat(result.priceAsOf()).isEqualTo(LocalDate.of(2026, 11, 16));
        } else {
            assertThat(result.priceAsOf()).isNull();
        }
    }

    private class Fixture {
        final LocalDate end = LocalDate.of(2025, 12, 31);
        final EnumMap<StandardAccount, AccountValue> accounts = new EnumMap<>(StandardAccount.class);
        Boolean preferred = false;
        StatementBasis basis = StatementBasis.CFS;
        String currency = "KRW";
        ShareDisclosure shares = new ShareDisclosure(100L, 10L, 90L, 0L, end, "SHARES-2025");
        PriceValue price = new PriceValue(new BigDecimal("90"), null, "KRW", DataStatus.AVAILABLE, null);
        PriceMeta priceMeta = new PriceMeta(LocalDate.of(2026, 11, 16), DataSource.MOCK,
            null, null, Instant.parse("2026-11-17T00:00:00Z"), "COMMON_STOCK");
        FinancialYear previous = new FinancialYear(2024, StatementBasis.CFS, "KRW",
            List.of(account(StandardAccount.REVENUE, new BigDecimal("1000"))), null);
        StatementMeta period = period(2025, basis);
        StatementMeta previousPeriod = period(2024, basis);
        AccountValue extra;

        Fixture() {
            amount(StandardAccount.REVENUE, new BigDecimal("1200"));
            amount(StandardAccount.OPERATING_PROFIT, new BigDecimal("180"));
            amount(StandardAccount.NET_INCOME, new BigDecimal("120"));
            amount(StandardAccount.PARENT_NET_INCOME, new BigDecimal("90"));
            amount(StandardAccount.PARENT_EQUITY, new BigDecimal("600"));
            amount(StandardAccount.TOTAL_ASSETS, new BigDecimal("1500"));
            amount(StandardAccount.TOTAL_LIABILITIES, new BigDecimal("700"));
            amount(StandardAccount.TOTAL_EQUITY, new BigDecimal("800"));
            amount(StandardAccount.BASIC_EPS, new BigDecimal("30"));
        }

        void amount(StandardAccount code, BigDecimal value) { accounts.put(code, account(code, value)); }

        List<MetricValue> values() {
            var all = new ArrayList<>(accounts.values());
            if (extra != null) { all.add(extra); }
            var current = new FinancialYear(2025, basis, currency, all, shares);
            var company = new CompanyDetail("990001", "99000001", "가상", Market.KOSPI,
                "C", "제조", 12, preferred, true, price);
            return calculator.calculate(company, current, previous, period, previousPeriod, priceMeta);
        }

        MetricValue metric(MetricCode code) {
            return values().stream().filter(v -> v.code() == code).findFirst().orElseThrow();
        }

        void assertReason(MetricReason reason, MetricCode... codes) {
            for (MetricCode code : codes) {
                assertThat(metric(code).reasonCode()).as(code.name()).isEqualTo(reason);
                assertThat(metric(code).value()).isNull();
            }
        }

        static AccountValue account(StandardAccount code, BigDecimal value) {
            return new AccountValue(code, code.label(), value, code.unit(), DataStatus.AVAILABLE, null, null);
        }

        static StatementMeta period(int year, StatementBasis basis) {
            return new StatementMeta(year, "11011", basis, "MOCK-" + year, LocalDate.of(year + 1, 3, 20),
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), DataSource.MOCK,
                Instant.parse("2026-11-17T00:00:00Z"));
        }
    }
}
