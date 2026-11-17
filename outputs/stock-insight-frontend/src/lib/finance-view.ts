import type { AccountValue, ApiEnvelope, Financials, FinancialYear, StatementMeta } from './types.ts';

export function displayName(name: string): string {
  return name.replace(/^\[가상\]\s*/, '');
}

export function basisName(basis: string): string {
  return basis === 'CFS' ? '연결 재무제표' : basis === 'OFS' ? '별도 재무제표' : '기준 확인 필요';
}

export interface RevenueView {
  year: FinancialYear;
  meta: StatementMeta;
  account: AccountValue | null;
  availableYears: number;
  basisChanged: boolean;
}

export function revenueView(response: ApiEnvelope<Financials>): RevenueView | null {
  if (!Array.isArray(response.data.years)) throw new Error('재무 자료 형식을 확인할 수 없습니다.');
  const years = [...response.data.years].sort((a, b) => b.fiscalYear - a.fiscalYear);
  const year = years[0];
  if (!year) return null;
  const meta = response.meta.financialPeriods.find(
    (period) => period.fiscalYear === year.fiscalYear && period.basis === year.basis,
  );
  if (!meta || !meta.periodEnd || !meta.receiptNo) throw new Error('재무 자료의 기준 정보를 확인할 수 없습니다.');
  if (!Array.isArray(year.accounts)) throw new Error('재무 계정 형식을 확인할 수 없습니다.');
  const account = year.accounts.find((item) => item.code === 'REVENUE') || null;
  return {
    year, meta, account, availableYears: years.length,
    basisChanged: new Set(years.map((item) => item.basis)).size > 1,
  };
}

// 표시 단위만 변환합니다. BigDecimal 문자열을 Number로 바꾸지 않아 큰 원 단위 값도 보존합니다.
export function formatWon(value: string): { eok: string; original: string } {
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value);
  if (!match) throw new Error('금액 형식이 올바르지 않습니다.');
  const negative = match[1] === '-';
  const integer = BigInt(match[2]);
  const decimals = match[3] || '';
  const fraction = decimals.replace(/0+$/, '');
  const original = `${negative ? '−' : ''}${integer.toLocaleString('ko-KR')}${fraction ? `.${fraction}` : ''}원`;
  // 억 원의 소수 둘째 자리까지 반올림합니다. 원본 문자열은 별도로 그대로 표시합니다.
  const hundredths = (integer + 500_000n) / 1_000_000n;
  const whole = hundredths / 100n;
  const cents = hundredths % 100n;
  const suffix = cents === 0n ? '' : `.${cents.toString().padStart(2, '0')}`;
  const sign = negative && hundredths !== 0n ? '−' : '';
  return { eok: `${sign}${whole.toLocaleString('ko-KR')}${suffix}`, original };
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '확인되지 않음';
  return value.slice(0, 10).replaceAll('-', '.');
}

export function formatCollectedAt(value: string | null | undefined): string {
  if (!value) return '확인되지 않음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '확인되지 않음';
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date);
}
