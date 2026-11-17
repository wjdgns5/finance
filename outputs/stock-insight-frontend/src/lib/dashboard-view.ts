import type { AccountValue, ApiMeta, FinancialYear, MetricValue, ProfitabilityHistory, StatementBasis } from './types.ts';
import { formatWon } from './finance-view.ts';

const DECIMAL = /^(-?)(\d+)(?:\.(\d+))?$/;

// 표시 자리수만 반올림합니다. 지표 공식은 백엔드에서만 계산합니다.
export function decimalText(value: string, digits = 2, signed = false): string {
  const match = DECIMAL.exec(value);
  if (!match) throw new Error('숫자 형식을 확인할 수 없습니다.');
  const scale = 10n ** BigInt(digits);
  const fraction = (match[3] || '').padEnd(digits + 1, '0');
  let scaled = BigInt(match[2]) * scale + BigInt(fraction.slice(0, digits) || '0');
  if (Number(fraction[digits]) >= 5) scaled++;
  const sign = scaled === 0n ? '' : match[1] ? '−' : signed ? '+' : '';
  const tail = digits ? (scaled % scale).toString().padStart(digits, '0').replace(/0+$/, '') : '';
  return `${sign}${(scaled / scale).toLocaleString('ko-KR')}${tail ? `.${tail}` : ''}`;
}

export function metricDisplay(metric: MetricValue | null | undefined) {
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null) {
    return { text: 'N/A', unit: '', reason: metric?.reason || '확인된 지표가 없습니다.', tone: 'neutral' };
  }
  try {
    const text = decimalText(metric.value, 2, metric.unit === 'PERCENT');
    return { text, unit: metric.unit === 'PERCENT' ? '%' : metric.unit === 'TIMES' ? '배' : '원/주', reason: null,
      tone: text.startsWith('−') ? 'negative' : text.startsWith('+') ? 'positive' : 'neutral' };
  } catch {
    return { text: 'N/A', unit: '', reason: '지표의 숫자 형식을 확인할 수 없습니다.', tone: 'neutral' };
  }
}

export function account(year: FinancialYear | undefined, code: string): AccountValue | null {
  if (!year || year.currency !== 'KRW') return null;
  const value = year.accounts.find(a => a.code === code);
  return value?.status === 'AVAILABLE' && value.unit === 'KRW' && value.value !== null && DECIMAL.test(value.value) ? value : null;
}

export function moneyDisplay(year: FinancialYear | undefined, code: string) {
  const value = account(year, code);
  return value?.value !== null && value?.value !== undefined ? formatWon(value.value) : null;
}

export function findMetric(metrics: MetricValue[] | undefined, code: string) {
  return metrics?.find(metric => metric.code === code) || null;
}

export function amountTone(raw: string | null | undefined): string {
  return raw && /^-/.test(raw) && !/^-0(?:\.0+)?$/.test(raw) ? 'negative' : 'neutral';
}

// SVG 좌표만 근사 Number를 사용합니다. 툴팁·표의 원본 금액은 문자열로 유지합니다.
export function chartAmount(raw: string | null | undefined): number | null {
  if (!raw || !DECIMAL.test(raw)) return null;
  const match = DECIMAL.exec(raw)!;
  const integer = match[2].padStart(9, '0');
  const scaled = `${match[1]}${integer.slice(0, -8)}.${integer.slice(-8)}${match[3] || ''}`;
  const value = Number(scaled);
  return Number.isFinite(value) ? value : null;
}

export function chartPercent(metric: MetricValue | null): number | null {
  if (!metric || metric.status !== 'AVAILABLE' || metric.unit !== 'PERCENT' || metric.value === null || !DECIMAL.test(metric.value)) return null;
  const value = Number(metric.value);
  return Number.isFinite(value) ? value : null;
}

export function yearSeries(years: FinancialYear[], profitability: ProfitabilityHistory | null, basis: StatementBasis) {
  if (!years.length) return [];
  const sorted = [...years].sort((a, b) => a.fiscalYear - b.fiscalYear);
  const first = sorted[0].fiscalYear, last = sorted[sorted.length - 1].fiscalYear;
  if (last - first > 30) throw new Error('차트 기간 범위를 확인할 수 없습니다.');
  return Array.from({ length: last - first + 1 }, (_, index) => {
    const fiscalYear = first + index;
    const year = sorted.find(y => y.fiscalYear === fiscalYear && y.basis === basis);
    const ratios = year && profitability?.years.find(y => y.fiscalYear === fiscalYear && y.basis === basis);
    const op = findMetric(ratios?.metrics, 'OPERATING_MARGIN');
    const net = findMetric(ratios?.metrics, 'NET_MARGIN');
    return { fiscalYear, year, revenue: chartAmount(account(year, 'REVENUE')?.value),
      assets: chartAmount(account(year, 'TOTAL_ASSETS')?.value), liabilities: chartAmount(account(year, 'TOTAL_LIABILITIES')?.value),
      equity: chartAmount(account(year, 'TOTAL_EQUITY')?.value),
      operatingMargin: year ? chartPercent(op) : null, netMargin: year ? chartPercent(net) : null,
      operatingCash: chartAmount(account(year, 'OPERATING_CASH_FLOW')?.value),
      investingCash: chartAmount(account(year, 'INVESTING_CASH_FLOW')?.value),
      financingCash: chartAmount(account(year, 'FINANCING_CASH_FLOW')?.value), op, net };
  });
}

export const ACCOUNT_LABELS: Record<string, string> = {
  REVENUE: '매출액', OPERATING_PROFIT: '영업이익', NET_INCOME: '당기순이익',
  PARENT_NET_INCOME: '지배주주 귀속 순이익', TOTAL_ASSETS: '자산총계', TOTAL_LIABILITIES: '부채총계',
  TOTAL_EQUITY: '자본총계', PARENT_EQUITY: '지배주주 지분', BASIC_EPS: '공시 기본주당이익',
  OPERATING_CASH_FLOW: '영업활동 현금흐름', INVESTING_CASH_FLOW: '투자활동 현금흐름', FINANCING_CASH_FLOW: '재무활동 현금흐름',
};

export function sourceName(source: string): string {
  return ({ MOCK: 'MOCK · 가상 데이터', DART: 'DART', KRX: 'KRX', FSC: '금융위원회' } as Record<string, string>)[source] || source;
}

export function samePeriods(primary: ApiMeta, secondary: ApiMeta, years: Pick<FinancialYear, 'fiscalYear' | 'basis'>[]): boolean {
  if (primary.mode !== secondary.mode) return false;
  return years.every(year => {
    const a = primary.financialPeriods.find(p => p.fiscalYear === year.fiscalYear && p.basis === year.basis);
    const b = secondary.financialPeriods.find(p => p.fiscalYear === year.fiscalYear && p.basis === year.basis);
    return !!a && !!b && a.receiptNo === b.receiptNo && a.periodStart === b.periodStart && a.periodEnd === b.periodEnd;
  });
}
