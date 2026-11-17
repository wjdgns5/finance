package com.stockinsight.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.domain.DataTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricQueryServiceTest {
    private final CompanyQueryService companies = mock(CompanyQueryService.class);
    private final FinancialMetricCalculator calculator = mock(FinancialMetricCalculator.class);
    private final MetricQueryService service = new MetricQueryService(companies, calculator);
    private final CompanySummary company = new CompanySummary("990001", "가상", Market.KOSPI, true);

    @Test
    void missingFinancialsReturnEmptyMetricsWithoutInventingAYear() {
        Meta meta = new Meta(DataMode.MOCK, List.of(DataSource.MOCK), Instant.EPOCH, List.of(), null, false);
        when(companies.financials("990001", 5)).thenReturn(Envelope.success(
            new Financials(company, DataStatus.NOT_COLLECTED, "없음", List.of()), meta));
        var result = service.metrics("990001");
        assertThat(result.data().status()).isEqualTo(DataStatus.NOT_COLLECTED);
        assertThat(result.data().fiscalYear()).isNull();
        assertThat(result.data().metrics()).isEmpty();
        assertThat(result.meta()).isEqualTo(meta);
        verifyNoInteractions(calculator);
        verify(companies, never()).detail(anyString());
    }

    @Test
    void latestYearUsesOnlyExactPreviousYearAndPreservesStaleAndPriceMetadata() {
        var old = year(2023);
        var latest = year(2025);
        var oldPeriod = period(2023);
        var latestPeriod = period(2025);
        var price = new PriceMeta(LocalDate.of(2026, 11, 16), DataSource.MOCK,
            null, null, Instant.EPOCH, "COMMON_STOCK");
        var meta = new Meta(DataMode.MOCK, List.of(DataSource.MOCK), Instant.EPOCH,
            List.of(oldPeriod, latestPeriod), price, false);
        var detail = new CompanyDetail("990001", "99000001", "가상", Market.KOSPI,
            "C", "제조", 12, false, true, null);
        when(companies.financials("990001", 5)).thenReturn(Envelope.success(
            new Financials(company, DataStatus.AVAILABLE, null, List.of(old, latest)), meta));
        when(companies.detail("990001")).thenReturn(Envelope.success(detail,
            new Meta(DataMode.MOCK, List.of(DataSource.MOCK), Instant.EPOCH, List.of(), price, true)));
        when(calculator.calculate(detail, latest, null, latestPeriod, null, price)).thenReturn(List.of());
        var result = service.metrics("990001");
        verify(calculator).calculate(detail, latest, null, latestPeriod, null, price);
        assertThat(result.data().fiscalYear()).isEqualTo(2025);
        assertThat(result.meta().financialPeriods()).containsExactly(latestPeriod);
        assertThat(result.meta().stale()).isTrue();
        assertThat(result.meta().market()).isEqualTo(price);
    }

    private FinancialYear year(int year) {
        return new FinancialYear(year, StatementBasis.CFS, "KRW", List.of(), null);
    }

    private StatementMeta period(int year) {
        return new StatementMeta(year, "11011", StatementBasis.CFS, "MOCK-" + year,
            LocalDate.of(year + 1, 3, 20), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
            DataSource.MOCK, Instant.EPOCH);
    }
}
