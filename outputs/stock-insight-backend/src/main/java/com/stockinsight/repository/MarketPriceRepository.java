package com.stockinsight.repository;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.MarketPrice;
import java.util.Optional;
import java.time.LocalDate;
import com.stockinsight.domain.DataTypes.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {
    Optional<MarketPrice> findFirstByCompanyOrderByClosePriceAsOfDescIdDesc(Company company);
    Optional<MarketPrice> findByCompanyAndClosePriceAsOfAndClosePriceSource(Company company, LocalDate date, DataSource source);
}
