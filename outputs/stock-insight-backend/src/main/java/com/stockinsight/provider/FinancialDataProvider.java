package com.stockinsight.provider;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import com.stockinsight.domain.DataTypes.DataMode;
import java.util.List;
import java.util.Optional;

public interface FinancialDataProvider {
    DataMode mode();
    List<Company> companies();
    List<FinancialStatement> financialStatements(Company company);
    default List<Company> search(String query, int limit) {
        return companies().stream().filter(c -> c.getStockCode().contains(query) || c.getName().contains(query)).limit(limit).toList();
    }
    default Optional<Company> company(String stockCode) {
        return companies().stream().filter(c -> c.getStockCode().equals(stockCode)).findFirst();
    }
    default List<String> matchingStockCodes(String query, int limit) {
        return search(query, limit).stream().map(Company::getStockCode).toList();
    }
}
