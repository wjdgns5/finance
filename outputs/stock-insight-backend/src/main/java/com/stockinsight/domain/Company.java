package com.stockinsight.domain;

import jakarta.persistence.*;
import java.time.Instant;
import static com.stockinsight.domain.DataTypes.*;

@Entity
@Table(name = "company", uniqueConstraints = {
    @UniqueConstraint(name = "uk_company_mode_stock", columnNames = {"data_mode", "stock_code"}),
    @UniqueConstraint(name = "uk_company_mode_corp", columnNames = {"data_mode", "corp_code"})
})
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;
    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Market market;
    @Column(name = "industry_code", nullable = false, length = 16)
    private String industryCode;
    @Column(name = "industry_name", nullable = false, length = 100)
    private String industryName;
    @Column(name = "fiscal_month", nullable = false)
    private int fiscalMonth;
    @Column(name = "has_preferred_shares")
    private Boolean hasPreferredShares;
    @Column(nullable = false)
    private boolean supported;
    @Enumerated(EnumType.STRING) @Column(name = "data_mode", nullable = false, length = 16)
    private DataMode dataMode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private DataSource source;
    @Column(name = "collected_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant collectedAt;

    protected Company() { }

    public Company(String stockCode, String corpCode, String name, Market market,
                   String industryCode, String industryName, int fiscalMonth,
                   Boolean hasPreferredShares, boolean supported, DataMode dataMode,
                   DataSource source, Instant collectedAt) {
        this.stockCode = stockCode;
        this.corpCode = corpCode;
        this.name = name;
        this.market = market;
        this.industryCode = industryCode;
        this.industryName = industryName;
        this.fiscalMonth = fiscalMonth;
        this.hasPreferredShares = hasPreferredShares;
        this.supported = supported;
        this.dataMode = dataMode;
        this.source = source;
        this.collectedAt = collectedAt;
    }

    public Long getId() { return id; }
    public String getStockCode() { return stockCode; }
    public String getCorpCode() { return corpCode; }
    public String getName() { return name; }
    public Market getMarket() { return market; }
    public String getIndustryCode() { return industryCode; }
    public String getIndustryName() { return industryName; }
    public int getFiscalMonth() { return fiscalMonth; }
    public Boolean getHasPreferredShares() { return hasPreferredShares; }
    public boolean isSupported() { return supported; }
    public DataMode getDataMode() { return dataMode; }
    public DataSource getSource() { return source; }
    public Instant getCollectedAt() { return collectedAt; }

    public void updateProfile(Company incoming) {
        this.name = incoming.name;
        this.market = incoming.market;
        this.industryCode = incoming.industryCode;
        this.industryName = incoming.industryName;
        this.fiscalMonth = incoming.fiscalMonth;
        this.supported = incoming.supported;
        this.collectedAt = incoming.collectedAt;
    }

    public void updatePreferredShares(Boolean preferred) { this.hasPreferredShares = preferred; }
}
