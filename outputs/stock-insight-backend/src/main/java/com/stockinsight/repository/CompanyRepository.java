package com.stockinsight.repository;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.DataTypes.DataMode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByDataModeAndStockCode(DataMode mode, String stockCode);

    @Query("""
        select c from Company c
        where c.dataMode = :mode and
          (lower(c.name) like lower(:pattern) escape '!' or c.stockCode like :pattern escape '!')
        order by case when c.stockCode = :keyword then 0 else 1 end, c.name, c.stockCode
        """)
    List<Company> search(@Param("mode") DataMode mode, @Param("keyword") String keyword,
                         @Param("pattern") String pattern, Pageable pageable);
}
