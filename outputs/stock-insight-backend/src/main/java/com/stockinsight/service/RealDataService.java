package com.stockinsight.service;

import com.stockinsight.api.ApiException;
import com.stockinsight.domain.Company;
import com.stockinsight.provider.*;
import com.stockinsight.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.domain.DataTypes.*;

@Service
@Profile("real")
public class RealDataService {
    private final FinancialDataProvider financialProvider;
    private final MarketDataProvider marketProvider;
    private final CompanyRepository companies;
    private final FinancialStatementRepository statements;
    private final MarketPriceRepository prices;
    private final DataSyncHistoryRepository histories;
    private final RealDataWriter writer;
    private final Clock clock;

    public RealDataService(FinancialDataProvider financialProvider, MarketDataProvider marketProvider,
        CompanyRepository companies, FinancialStatementRepository statements, MarketPriceRepository prices,
        DataSyncHistoryRepository histories, RealDataWriter writer, Clock clock) {
        this.financialProvider = financialProvider; this.marketProvider = marketProvider; this.companies = companies;
        this.statements = statements; this.prices = prices; this.histories = histories; this.writer = writer; this.clock = clock;
    }

    public synchronized void prepareSearch(String query, int limit) {
        String keyword = query.strip();
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var cached = companies.search(DataMode.REAL, keyword, pattern, PageRequest.of(0, limit));
        if (keyword.matches("[0-9]{6}") && cached.stream().anyMatch(c -> c.getStockCode().equals(keyword))) { return; }
        try {
            // 검색 결과 일부가 이미 DB에 있어도 나머지 일치 기업을 누락하지 않습니다.
            for (String stockCode : financialProvider.matchingStockCodes(keyword, limit)) { ensureCompany(stockCode); }
        } catch (ProviderException ex) {
            if (cached.isEmpty()) { throw ex; }
        }
    }

    public synchronized Company ensureCompany(String stockCode) {
        return companies.findByDataModeAndStockCode(DataMode.REAL, stockCode).orElseGet(() -> writer.company(
            financialProvider.company(stockCode).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "COMPANY_NOT_FOUND", "해당 종목코드의 상장 기업을 찾을 수 없습니다."))));
    }

    public synchronized void prepareDetail(String stockCode) { market(ensureCompany(stockCode), false); }

    public synchronized void prepareFinancials(String stockCode) {
        Company company = ensureCompany(stockCode);
        supported(company);
        boolean cached = !statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(company, "11011").isEmpty();
        if (!cached && recent(company, SyncType.FINANCIALS, Duration.ofMinutes(5))) {
            histories.findFirstByCompanyAndSyncTypeOrderByCompletedAtDescIdDesc(company, SyncType.FINANCIALS)
                .filter(h -> h.getStatus() == SyncStatus.FAILED).ifPresent(h -> {
                    throw new ApiException(providerStatus(h.getErrorCode()), h.getErrorCode(), h.getErrorMessage());
                });
        }
        if (!cached && !recent(company, SyncType.FINANCIALS, Duration.ofMinutes(5))) {
            SyncOutcome result = financials(company);
            if (result.status() == SyncStatus.FAILED) {
                throw new ApiException(providerStatus(result.errorCode()), result.errorCode(), result.message());
            }
        }
        market(company, false);
    }

    public synchronized CollectionResult refresh(String stockCode) {
        Company company = ensureCompany(stockCode);
        supported(company);
        List<SyncOutcome> results = new ArrayList<>();
        var started = clock.instant();
        try {
            company = writer.company(financialProvider.company(stockCode).orElseThrow(() -> new ProviderException(
                HttpStatus.BAD_GATEWAY, "DART_COMPANY_UNAVAILABLE", "기업 개황을 다시 확인할 수 없습니다.")));
            supported(company);
        } catch (ProviderException ex) {
            writer.failure(company, SyncType.FINANCIALS, started, ex.getCode(), ex.getMessage());
            results.add(new SyncOutcome(SyncType.FINANCIALS, SyncStatus.FAILED, 0, ex.getCode(), ex.getMessage()));
            results.add(market(company, true));
            return new CollectionResult(stockCode, results);
        }
        // 새 공시 수집 실패와 시세 실패를 서로 분리하고 기존 정상 자료를 삭제하지 않습니다.
        results.add(financials(company));
        results.add(market(company, true));
        return new CollectionResult(stockCode, results);
    }

    private SyncOutcome financials(Company company) {
        var started = clock.instant();
        try {
            int saved = writer.financials(company, financialProvider.financialStatements(company), started);
            return new SyncOutcome(SyncType.FINANCIALS, SyncStatus.SUCCESS, saved, null,
                saved == 0 ? "해당 범위의 수집 가능한 연간 자료가 없습니다." : "연간 자료를 저장했습니다.");
        } catch (ProviderException ex) {
            writer.failure(company, SyncType.FINANCIALS, started, ex.getCode(), ex.getMessage());
            return new SyncOutcome(SyncType.FINANCIALS, SyncStatus.FAILED, 0, ex.getCode(), ex.getMessage());
        }
    }

    private SyncOutcome market(Company company, boolean force) {
        if (!force && (prices.findFirstByCompanyOrderByClosePriceAsOfDescIdDesc(company).isPresent()
            || recent(company, SyncType.MARKET, Duration.ofMinutes(5)))) {
            return new SyncOutcome(SyncType.MARKET, SyncStatus.SUCCESS, 0, null, "저장된 시세·수집 이력을 재사용했습니다.");
        }
        var started = clock.instant();
        try {
            var price = marketProvider.latestPrice(company);
            if (price.isEmpty()) {
                throw new ProviderException(HttpStatus.BAD_GATEWAY, "FSC_NO_DATA", "최근 30일 범위의 확인 가능한 종가가 없습니다.");
            }
            writer.price(company, price.get(), started);
            return new SyncOutcome(SyncType.MARKET, SyncStatus.SUCCESS, 1, null, "종가 스냅샷을 저장했습니다.");
        } catch (ProviderException ex) {
            writer.failure(company, SyncType.MARKET, started, ex.getCode(), ex.getMessage());
            return new SyncOutcome(SyncType.MARKET, SyncStatus.FAILED, 0, ex.getCode(), ex.getMessage());
        }
    }

    private boolean recent(Company company, SyncType type, Duration duration) {
        return histories.findFirstByCompanyAndSyncTypeOrderByCompletedAtDescIdDesc(company, type)
            .map(h -> Duration.between(h.getCompletedAt(), clock.instant()).compareTo(duration) < 0).orElse(false);
    }

    private void supported(Company company) {
        if (!company.isSupported()) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
            "UNSUPPORTED_INDUSTRY", "금융업은 계정 구조가 달라 현재 재무 분석을 지원하지 않습니다."); }
    }

    private HttpStatus providerStatus(String code) {
        return switch (code) {
            case "PROVIDER_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "DART_KEY_MISSING", "DART_AUTH_ERROR", "DART_RATE_LIMIT", "PROVIDER_INTERRUPTED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }
}
