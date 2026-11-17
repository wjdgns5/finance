import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatWon, revenueView } from './finance-view.ts';
import type { ApiEnvelope, Financials, FinancialYear, StatementMeta } from './types.ts';

test('큰 원 단위 금액을 Number 정밀도 손실 없이 표시한다', () => {
  const result = formatWon('900719925474099312345.000000');
  assert.equal(result.original, '900,719,925,474,099,312,345원');
  assert.equal(result.eok, '9,007,199,254,740.99');
});

test('실제 0과 음수의 부호 및 표시 반올림을 보존한다', () => {
  assert.deepEqual(formatWon('0.000000'), { eok: '0', original: '0원' });
  assert.equal(formatWon('-123500000.000000').eok, '−1.24');
  assert.equal(formatWon('123499999.999999').eok, '1.23');
});

test('잘못된 금액을 0으로 바꾸지 않는다', () => {
  for (const value of ['', 'N/A', '1,000', 'Infinity', '1e10']) {
    assert.throws(() => formatWon(value));
  }
});

function fixture(years: FinancialYear[], periods: StatementMeta[]): ApiEnvelope<Financials> {
  return {
    data: { company: { stockCode: '990001', name: '[가상] 테스트', market: 'KOSPI', supported: true },
      status: 'AVAILABLE', reason: null, years },
    meta: { mode: 'MOCK', sources: ['MOCK'], collectedAt: null, financialPeriods: periods, market: null, stale: false },
    error: null,
  };
}

function year(fiscalYear: number, basis: 'CFS' | 'OFS'): FinancialYear {
  return { fiscalYear, basis, currency: 'KRW', accounts: [],
    shares: { issuedCommonShares: null, treasuryCommonShares: null, outstandingCommonShares: null,
      preferredShares: null, asOf: null, receiptNo: null } };
}

function period(fiscalYear: number, basis: 'CFS' | 'OFS'): StatementMeta {
  return { fiscalYear, basis, reportCode: '11011', receiptNo: 'MOCK-TEST', filedAt: '2026-03-20',
    periodStart: '2025-01-01', periodEnd: '2025-12-31', source: 'MOCK', collectedAt: '2026-11-17T00:00:00Z' };
}

test('최신 연도와 같은 연결/별도 메타데이터를 선택한다', () => {
  const result = revenueView(fixture([year(2024, 'OFS'), year(2025, 'CFS')], [period(2024, 'OFS'), period(2025, 'CFS')]));
  assert.equal(result?.year.fiscalYear, 2025);
  assert.equal(result?.meta.basis, 'CFS');
  assert.equal(result?.basisChanged, true);
  assert.equal(result?.account, null);
});

test('자료가 없으면 빈 상태를 유지하고 메타데이터 불일치는 거부한다', () => {
  assert.equal(revenueView(fixture([], [])), null);
  assert.throws(() => revenueView(fixture([year(2025, 'CFS')], [period(2025, 'OFS')])));
});
