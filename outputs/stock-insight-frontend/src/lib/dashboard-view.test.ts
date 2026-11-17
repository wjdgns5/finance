import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chartAmount, decimalText, metricDisplay, samePeriods, yearSeries } from './dashboard-view.ts';
import type { ApiMeta, FinancialYear, MetricValue, ProfitabilityHistory } from './types.ts';

function metric(value: string | null, status: MetricValue['status'] = 'AVAILABLE'): MetricValue {
  return { code: 'OPERATING_MARGIN', label: '영업이익률', value, status, unit: 'PERCENT', reasonCode: null,
    reason: status === 'AVAILABLE' ? null : '계정이 없습니다.', formula: '공식', usedAccounts: ['REVENUE'],
    explanation: '설명', naConditions: '매출 0 이하', basisNote: '연간', priceAsOf: null };
}
function year(fiscalYear: number, basis: FinancialYear['basis'], revenue: string | null): FinancialYear {
  return { fiscalYear, basis, currency: 'KRW', accounts: [{ code: 'REVENUE', label: '매출액', value: revenue,
    unit: 'KRW', status: revenue === null ? 'NOT_COLLECTED' : 'AVAILABLE', reason: null, original: null }],
    shares: { issuedCommonShares: null, treasuryCommonShares: null, outstandingCommonShares: null, preferredShares: null, asOf: null, receiptNo: null } };
}

test('지표 표시에서 API 백분율을 다시 100배 하지 않고 부호를 표시한다', () => {
  assert.equal(metricDisplay(metric('15.000000')).text, '+15');
  assert.equal(metricDisplay(metric('-4.444444')).text, '−4.44');
  assert.equal(metricDisplay(metric('0.000000')).text, '0');
});
test('상태가 산출 불가이면 숫자가 남아 있어도 N/A와 사유를 표시한다', () => {
  assert.equal(metricDisplay(metric('0', 'NOT_APPLICABLE')).text, 'N/A');
  assert.equal(metricDisplay(metric(null, 'NOT_COLLECTED')).reason, '계정이 없습니다.');
  assert.equal(metricDisplay(metric('NaN')).text, 'N/A');
});
test('큰 주당 금액과 반올림을 BigInt로 표시한다', () => {
  assert.equal(decimalText('900719925474099312345.995'), '900,719,925,474,099,312,346');
  assert.equal(decimalText('-1.235'), '−1.24');
  assert.equal(decimalText('-0.000001'), '0');
  assert.throws(() => decimalText('1e20'));
});
test('차트 좌표만 억 원으로 변환하고 결측과 실제 0을 구분한다', () => {
  assert.equal(chartAmount('123456789.123456'), 1.23456789123456);
  assert.equal(chartAmount('0.000000'), 0);
  assert.equal(chartAmount(null), null);
  assert.equal(chartAmount('bad'), null);
});
test('같은 차트에 연결·별도를 섞거나 빠진 연도의 선을 연결하지 않는다', () => {
  const rows = yearSeries([year(2021, 'CFS', '100000000'), year(2022, 'OFS', '200000000'), year(2024, 'CFS', '0')], null, 'CFS');
  assert.deepEqual(rows.map(r => r.fiscalYear), [2021, 2022, 2023, 2024]);
  assert.deepEqual(rows.map(r => r.revenue), [1, null, null, 0]);
});
test('이익률도 같은 연도·기준의 백엔드 응답에서만 읽는다', () => {
  const data: ProfitabilityHistory = { company: { stockCode: '990001', name: '가상', market: 'KOSPI', supported: true },
    status: 'AVAILABLE', reason: null, years: [{ fiscalYear: 2025, basis: 'OFS', metrics: [metric('15')] }] };
  const rows = yearSeries([year(2025, 'CFS', '100000000')], data, 'CFS');
  assert.equal(rows[0].operatingMargin, null);
  assert.equal(rows[0].revenue, 1);
});
test('서로 다른 접수번호·기간·모드의 지표를 한 화면에 조합하지 않는다', () => {
  const primary: ApiMeta = { mode: 'REAL', sources: ['DART'], collectedAt: null, stale: false, market: null,
    financialPeriods: [{ fiscalYear: 2025, basis: 'CFS', reportCode: '11011', receiptNo: '20260312001224',
      filedAt: '2026-03-12', periodStart: '2025-01-01', periodEnd: '2025-12-31', source: 'DART', collectedAt: '2026-11-17T00:00:00Z' }] };
  const required = [{ fiscalYear: 2025, basis: 'CFS' as const }];
  assert.equal(samePeriods(primary, primary, required), true);
  assert.equal(samePeriods(primary, { ...primary, mode: 'MOCK' }, required), false);
  assert.equal(samePeriods(primary, { ...primary, financialPeriods: [{ ...primary.financialPeriods[0], receiptNo: 'different' }] }, required), false);
  assert.equal(samePeriods(primary, { ...primary, financialPeriods: [] }, required), false);
});
