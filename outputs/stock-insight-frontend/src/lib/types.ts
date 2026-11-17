export type DataMode = 'MOCK' | 'REAL';
export type DataSource = 'MOCK' | 'DART' | 'KRX' | 'FSC';
export type StatementBasis = 'CFS' | 'OFS';
export type DataStatus = 'AVAILABLE' | 'NOT_COLLECTED' | 'NOT_APPLICABLE' | 'UNSUPPORTED';

export interface CompanySummary {
  stockCode: string;
  name: string;
  market: 'KOSPI' | 'KOSDAQ' | 'KONEX';
  supported: boolean;
}

export interface CompanyDetail extends CompanySummary {
  corpCode: string;
  industryCode: string;
  industryName: string;
  fiscalMonth: number;
  hasPreferredShares: boolean | null;
  price: {
    closePrice: string | null;
    marketCap: string | null;
    currency: string;
    status: DataStatus;
    reason: string | null;
  };
}

export interface StatementMeta {
  fiscalYear: number;
  reportCode: string;
  basis: StatementBasis;
  receiptNo: string;
  filedAt: string;
  periodStart: string;
  periodEnd: string;
  source: DataSource;
  collectedAt: string;
}

export interface ApiMeta {
  mode: DataMode;
  sources: DataSource[];
  collectedAt: string | null;
  financialPeriods: StatementMeta[];
  market: {
    closePriceAsOf: string;
    closePriceSource: DataSource;
    marketCapAsOf: string | null;
    marketCapSource: DataSource | null;
    collectedAt: string;
    scope: string;
  } | null;
  stale: boolean;
}

export interface ApiEnvelope<T> {
  data: T;
  meta: ApiMeta;
  error: null;
}

export interface AccountValue {
  code: string;
  label: string;
  value: string | null;
  unit: 'KRW' | 'KRW_PER_SHARE';
  status: DataStatus;
  reason: string | null;
  original: {
    accountId: string;
    accountName: string;
    accountDetail: string | null;
    amount: string | null;
  } | null;
}

export interface FinancialYear {
  fiscalYear: number;
  basis: StatementBasis;
  currency: string;
  accounts: AccountValue[];
  shares: {
    issuedCommonShares: string | null;
    treasuryCommonShares: string | null;
    outstandingCommonShares: string | null;
    preferredShares: string | null;
    asOf: string | null;
    receiptNo: string | null;
  };
  normalizationWarnings?: string[];
}

export interface Financials {
  company: CompanySummary;
  status: DataStatus;
  reason: string | null;
  years: FinancialYear[];
}

export type MetricCode = 'REVENUE_GROWTH' | 'OPERATING_MARGIN' | 'NET_MARGIN' | 'ROE' | 'ROA' | 'DEBT_RATIO' | 'EPS' | 'BPS' | 'PER' | 'PBR';
export interface MetricValue {
  code: MetricCode;
  label: string;
  value: string | null;
  unit: 'PERCENT' | 'KRW_PER_SHARE' | 'TIMES';
  status: DataStatus;
  reasonCode: string | null;
  reason: string | null;
  formula: string;
  usedAccounts: string[];
  explanation: string;
  naConditions: string;
  basisNote: string;
  priceAsOf: string | null;
}
export interface Metrics {
  company: CompanySummary;
  status: DataStatus;
  reason: string | null;
  fiscalYear: number | null;
  basis: StatementBasis | null;
  currency: string;
  shares: FinancialYear['shares'] | null;
  priceNote: string | null;
  metrics: MetricValue[];
}
export interface ProfitabilityYear {
  fiscalYear: number;
  basis: StatementBasis;
  metrics: MetricValue[];
}
export interface ProfitabilityHistory {
  company: CompanySummary;
  status: DataStatus;
  reason: string | null;
  years: ProfitabilityYear[];
}
