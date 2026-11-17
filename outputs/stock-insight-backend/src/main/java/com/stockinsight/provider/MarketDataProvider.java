package com.stockinsight.provider;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.MarketPrice;
import java.util.Optional;

public interface MarketDataProvider {
    Optional<MarketPrice> latestPrice(Company company);
}
