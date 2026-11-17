package com.stockinsight.provider;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.MarketPrice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static com.stockinsight.domain.DataTypes.DataSource.MOCK;

@Component
@Profile("mock")
@ConditionalOnProperty(name = "app.data-mode", havingValue = "MOCK")
public class MockMarketDataProvider implements MarketDataProvider {
    private final Clock clock;

    public MockMarketDataProvider(Clock clock) { this.clock = clock; }

    @Override
    public Optional<MarketPrice> latestPrice(Company company) {
        if (!company.isSupported() || company.getStockCode().equals("990004")) {
            return Optional.empty();
        }
        LocalDate asOf = LocalDate.of(2026, 11, 16);
        return Optional.of(new MarketPrice(company, new BigDecimal("32000"), asOf, MOCK,
            new BigDecimal("320000000000"), asOf, MOCK, "KRW", clock.instant()));
    }
}
