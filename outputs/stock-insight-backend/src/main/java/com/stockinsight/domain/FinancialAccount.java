package com.stockinsight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import static com.stockinsight.domain.DataTypes.*;

@Entity
@Table(name = "financial_account", uniqueConstraints = @UniqueConstraint(
    name = "uk_account_statement_code", columnNames = {"statement_id", "standard_code"}))
public class FinancialAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private FinancialStatement statement;
    @Enumerated(EnumType.STRING) @Column(name = "standard_code", nullable = false, length = 48)
    private StandardAccount code;
    @Column(precision = 30, scale = 6)
    private BigDecimal amount;
    @Column(name = "original_account_id", nullable = false, length = 256)
    private String originalAccountId;
    @Column(name = "original_account_name", nullable = false, length = 256)
    private String originalAccountName;
    @Column(name = "original_account_detail", length = 1000)
    private String originalAccountDetail;
    @Column(name = "original_amount", length = 100)
    private String originalAmount;

    protected FinancialAccount() { }

    FinancialAccount(FinancialStatement statement, StandardAccount code, BigDecimal amount,
                     String originalAccountId, String originalAccountName,
                     String originalAccountDetail, String originalAmount) {
        this.statement = statement;
        this.code = code;
        this.amount = amount;
        this.originalAccountId = originalAccountId;
        this.originalAccountName = originalAccountName;
        this.originalAccountDetail = originalAccountDetail;
        this.originalAmount = originalAmount;
    }

    public StandardAccount getCode() { return code; }
    public BigDecimal getAmount() { return amount; }
    public String getOriginalAccountId() { return originalAccountId; }
    public String getOriginalAccountName() { return originalAccountName; }
    public String getOriginalAccountDetail() { return originalAccountDetail; }
    public String getOriginalAmount() { return originalAmount; }

    void updateFrom(FinancialAccount incoming) {
        this.amount = incoming.amount;
        this.originalAccountId = incoming.originalAccountId;
        this.originalAccountName = incoming.originalAccountName;
        this.originalAccountDetail = incoming.originalAccountDetail;
        this.originalAmount = incoming.originalAmount;
    }
}
