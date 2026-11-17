package com.stockinsight.domain;

public final class DataTypes {
    private DataTypes() { }

    public enum DataMode { MOCK, REAL }
    public enum DataSource { MOCK, DART, KRX, FSC }
    public enum StatementBasis { CFS, OFS }
    public enum Market { KOSPI, KOSDAQ, KONEX }
    public enum DataStatus { AVAILABLE, NOT_COLLECTED, NOT_APPLICABLE, UNSUPPORTED }
    public enum SyncStatus { SUCCESS, FAILED }
    public enum SyncType { FINANCIALS, MARKET }
    public enum AccountUnit { KRW, KRW_PER_SHARE }

    public enum StandardAccount {
        REVENUE("매출액", AccountUnit.KRW),
        OPERATING_PROFIT("영업이익", AccountUnit.KRW),
        NET_INCOME("당기순이익", AccountUnit.KRW),
        PARENT_NET_INCOME("지배주주 귀속 순이익", AccountUnit.KRW),
        TOTAL_ASSETS("자산총계", AccountUnit.KRW),
        TOTAL_LIABILITIES("부채총계", AccountUnit.KRW),
        TOTAL_EQUITY("자본총계", AccountUnit.KRW),
        PARENT_EQUITY("지배주주 지분", AccountUnit.KRW),
        OPERATING_CASH_FLOW("영업활동 현금흐름", AccountUnit.KRW),
        INVESTING_CASH_FLOW("투자활동 현금흐름", AccountUnit.KRW),
        FINANCING_CASH_FLOW("재무활동 현금흐름", AccountUnit.KRW),
        BASIC_EPS("공시 기본주당이익", AccountUnit.KRW_PER_SHARE);

        private final String label;
        private final AccountUnit unit;

        StandardAccount(String label, AccountUnit unit) {
            this.label = label;
            this.unit = unit;
        }

        public String label() { return label; }
        public AccountUnit unit() { return unit; }
    }
}
