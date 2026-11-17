package com.stockinsight.service;

import com.stockinsight.domain.*;
import com.stockinsight.provider.*;
import com.stockinsight.repository.*;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.stockinsight.domain.DataTypes.*;

@Service
@Profile("mock")
public class MockSeedService implements ApplicationRunner {
    private final FinancialDataProvider financialProvider;
    private final MarketDataProvider marketProvider;
    private final CompanyRepository companies;
    private final FinancialStatementRepository statements;
    private final MarketPriceRepository prices;
    private final DataSyncHistoryRepository histories;
    private final Clock clock;

    public MockSeedService(FinancialDataProvider financialProvider, MarketDataProvider marketProvider,
                            CompanyRepository companies, FinancialStatementRepository statements,
                            MarketPriceRepository prices, DataSyncHistoryRepository histories, Clock clock) {
        this.financialProvider = financialProvider;
        this.marketProvider = marketProvider;
        this.companies = companies;
        this.statements = statements;
        this.prices = prices;
        this.histories = histories;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 최초 적재 전체를 한 트랜잭션으로 묶습니다. 재시작 때 기존 자료를 덮어쓰지 않습니다.
        for (Company candidate : financialProvider.companies()) {
            if (companies.findByDataModeAndStockCode(financialProvider.mode(), candidate.getStockCode()).isPresent()) {
                continue;
            }
            Instant startedAt = clock.instant();
            Company company = companies.save(candidate);
            var financials = financialProvider.financialStatements(company);
            statements.saveAll(financials);
            histories.save(new DataSyncHistory(company, SyncType.FINANCIALS, SyncStatus.SUCCESS,
                DataSource.MOCK, startedAt, clock.instant(), financials.size(), null, null));
            var price = marketProvider.latestPrice(company);
            price.ifPresent(prices::save);
            histories.save(new DataSyncHistory(company, SyncType.MARKET, SyncStatus.SUCCESS,
                DataSource.MOCK, startedAt, clock.instant(), price.isPresent() ? 1 : 0, null, null));
        }
    }
}
