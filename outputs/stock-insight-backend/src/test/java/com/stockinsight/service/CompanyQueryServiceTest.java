package com.stockinsight.service;

import com.stockinsight.api.ApiException;
import com.stockinsight.domain.*;
import com.stockinsight.provider.MockFinancialDataProvider;
import com.stockinsight.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import static com.stockinsight.domain.DataTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyQueryServiceTest {
    @Mock CompanyRepository companies;
    @Mock FinancialStatementRepository statements;
    @Mock MarketPriceRepository prices;
    @Mock DataSyncHistoryRepository histories;
    MockFinancialDataProvider provider;
    CompanyQueryService service;

    @BeforeEach
    void setup() {
        provider = new MockFinancialDataProvider(Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));
        service = new CompanyQueryService(companies, statements, prices, histories, provider);
    }

    private Company stubCompany(String code) {
        Company company = provider.companies().stream().filter(c -> c.getStockCode().equals(code)).findFirst().orElseThrow();
        when(companies.findByDataModeAndStockCode(DataMode.MOCK, code)).thenReturn(Optional.of(company));
        return company;
    }

    @Test
    void searchEscapesWildcardsAndScopesToMock() {
        service.search(" %_! ", 10);
        verify(companies).search(eq(DataMode.MOCK), eq("%_!"), eq("%!%!_!!%"), any(Pageable.class));
    }

    @Test
    void unknownCompanyIs404() {
        assertThatThrownBy(() -> service.detail("000000"))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getStatus().value()).isEqualTo(404);
                assertThat(ex.getCode()).isEqualTo("COMPANY_NOT_FOUND");
            });
    }

    @Test
    void prefersConsolidatedAndReturnsExactlyFiveDistinctYears() {
        Company company = stubCompany("990001");
        when(statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(company, "11011"))
            .thenReturn(provider.financialStatements(company));
        var result = service.financials("990001", 5);
        assertThat(result.data().years()).extracting(y -> y.fiscalYear()).containsExactly(2025, 2024, 2023, 2022, 2021);
        assertThat(result.data().years()).allMatch(y -> y.basis() == StatementBasis.CFS);
        assertThat(result.meta().financialPeriods()).hasSize(5);
        assertThat(result.meta().financialPeriods().getFirst().receiptNo()).startsWith("MOCK-");
        assertThat(result.meta().mode()).isEqualTo(DataMode.MOCK);
    }

    @Test
    void preservesBasisChangesAndMissingValuesWithoutInventingYears() {
        Company company = stubCompany("990004");
        when(statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(company, "11011"))
            .thenReturn(provider.financialStatements(company));
        var result = service.financials("990004", 5);
        assertThat(result.data().years()).hasSize(2);
        assertThat(result.meta().financialPeriods()).extracting(p -> p.basis())
            .containsExactly(StatementBasis.CFS, StatementBasis.OFS);
        var eps = result.data().years().getFirst().accounts().stream()
            .filter(a -> a.code() == StandardAccount.BASIC_EPS).findFirst().orElseThrow();
        assertThat(eps.value()).isNull();
        assertThat(eps.status()).isEqualTo(DataStatus.NOT_COLLECTED);
        assertThat(eps.reason()).isNotBlank();
        assertThat(result.data().years().getFirst().shares().preferredShares()).isNull();
        assertThat(result.meta().financialPeriods().getFirst().periodEnd().getMonthValue()).isEqualTo(3);
    }

    @Test
    void negativeAndZeroReportedValuesRemainAvailable() {
        Company company = stubCompany("990002");
        when(statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(company, "11011"))
            .thenReturn(provider.financialStatements(company));
        var accounts = service.financials("990002", 1).data().years().getFirst().accounts();
        var eps = accounts.stream().filter(a -> a.code() == StandardAccount.BASIC_EPS).findFirst().orElseThrow();
        var cash = accounts.stream().filter(a -> a.code() == StandardAccount.FINANCING_CASH_FLOW).findFirst().orElseThrow();
        assertThat(eps.value()).isEqualByComparingTo("-600");
        assertThat(eps.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(cash.value()).isZero();
        assertThat(cash.status()).isEqualTo(DataStatus.AVAILABLE);
    }

    @Test
    void financialIndustryIs422AndDoesNotQueryStatements() {
        stubCompany("990005");
        assertThatThrownBy(() -> service.financials("990005", 5))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(422));
        verifyNoInteractions(statements);
    }

    @Test
    void missingFinancialsHaveExplicitNotCollectedStatus() {
        stubCompany("990001");
        var response = service.financials("990001", 5);
        assertThat(response.data().years()).isEmpty();
        assertThat(response.data().status()).isEqualTo(DataStatus.NOT_COLLECTED);
    }

    @Test
    void missingPriceDoesNotReplaceValuesWithZero() {
        stubCompany("990004");
        var response = service.detail("990004");
        assertThat(response.data().price().closePrice()).isNull();
        assertThat(response.data().price().status()).isEqualTo(DataStatus.NOT_COLLECTED);
        assertThat(response.meta().market()).isNull();
    }

    @Test
    void latestSyncFailureMarksExistingDataStale() {
        Company company = stubCompany("990001");
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        when(histories.findFirstByCompanyAndSyncTypeOrderByCompletedAtDescIdDesc(company, SyncType.FINANCIALS))
            .thenReturn(Optional.of(new DataSyncHistory(company, SyncType.FINANCIALS, SyncStatus.FAILED,
                DataSource.MOCK, now, now, 0, "TEST_FAILURE", "테스트 실패 이력")));
        assertThat(service.detail("990001").meta().stale()).isTrue();
    }
}
