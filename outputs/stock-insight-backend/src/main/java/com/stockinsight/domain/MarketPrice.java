package com.stockinsight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import static com.stockinsight.domain.DataTypes.*;

@Entity
@Table(name = "market_price", uniqueConstraints = @UniqueConstraint(
    name = "uk_market_snapshot", columnNames = {"company_id", "close_price_as_of", "close_price_source"}))
public class MarketPrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(name = "close_price", nullable = false, precision = 30, scale = 6)
    private BigDecimal closePrice;
    @Column(name = "close_price_as_of", nullable = false)
    private LocalDate closePriceAsOf;
    @Enumerated(EnumType.STRING) @Column(name = "close_price_source", nullable = false, length = 16)
    private DataSource closePriceSource;
    @Column(name = "market_cap", precision = 30, scale = 0)
    private BigDecimal marketCap;
    @Column(name = "market_cap_as_of")
    private LocalDate marketCapAsOf;
    @Enumerated(EnumType.STRING) @Column(name = "market_cap_source", length = 16)
    private DataSource marketCapSource;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "collected_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant collectedAt;

    protected MarketPrice() { }

    public MarketPrice(Company company, BigDecimal closePrice, LocalDate closePriceAsOf,
                       DataSource closePriceSource, BigDecimal marketCap, LocalDate marketCapAsOf,
                       DataSource marketCapSource, String currency, Instant collectedAt) {
        this.company = company;
        this.closePrice = closePrice;
        this.closePriceAsOf = closePriceAsOf;
        this.closePriceSource = closePriceSource;
        this.marketCap = marketCap;
        this.marketCapAsOf = marketCapAsOf;
        this.marketCapSource = marketCapSource;
        this.currency = currency;
        this.collectedAt = collectedAt;
    }

    public BigDecimal getClosePrice() { return closePrice; }
    public LocalDate getClosePriceAsOf() { return closePriceAsOf; }
    public DataSource getClosePriceSource() { return closePriceSource; }
    public BigDecimal getMarketCap() { return marketCap; }
    public LocalDate getMarketCapAsOf() { return marketCapAsOf; }
    public DataSource getMarketCapSource() { return marketCapSource; }
    public String getCurrency() { return currency; }
    public Instant getCollectedAt() { return collectedAt; }

    public void updateFrom(MarketPrice incoming) {
        this.closePrice = incoming.closePrice;
        this.marketCap = incoming.marketCap;
        this.marketCapAsOf = incoming.marketCapAsOf;
        this.marketCapSource = incoming.marketCapSource;
        this.currency = incoming.currency;
        this.collectedAt = incoming.collectedAt;
    }
}
