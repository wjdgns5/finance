import type { ApiMeta } from './types.ts';

type ObjectValue = Record<string, unknown>;
const object = (value: unknown): value is ObjectValue => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = (value: unknown): value is string => typeof value === 'string';
const nullableText = (value: unknown) => value === null || text(value);
const texts = (value: unknown) => Array.isArray(value) && value.every(text);
const status = (value: unknown) => ['AVAILABLE', 'NOT_COLLECTED', 'NOT_APPLICABLE', 'UNSUPPORTED'].includes(value as string);
const basis = (value: unknown) => value === 'CFS' || value === 'OFS';

export function validMeta(value: unknown): value is ApiMeta {
  if (!object(value) || !['MOCK', 'REAL'].includes(value.mode as string) || !texts(value.sources)
    || !nullableText(value.collectedAt) || typeof value.stale !== 'boolean' || !Array.isArray(value.financialPeriods)) return false;
  if (!value.financialPeriods.every(p => object(p) && Number.isInteger(p.fiscalYear) && basis(p.basis)
    && text(p.receiptNo) && text(p.reportCode) && text(p.periodStart) && text(p.periodEnd)
    && text(p.filedAt) && text(p.source) && text(p.collectedAt))) return false;
  const market = value.market;
  return market === null || (object(market) && text(market.closePriceAsOf) && text(market.closePriceSource)
    && nullableText(market.marketCapAsOf) && nullableText(market.marketCapSource) && text(market.collectedAt) && text(market.scope));
}

function company(value: unknown) {
  return object(value) && text(value.stockCode) && /^[0-9]{6}$/.test(value.stockCode) && text(value.name)
    && text(value.market) && typeof value.supported === 'boolean';
}

function metric(value: unknown) {
  return object(value) && text(value.code) && text(value.label) && nullableText(value.value) && text(value.unit)
    && status(value.status) && nullableText(value.reason) && nullableText(value.reasonCode) && text(value.formula)
    && texts(value.usedAccounts) && text(value.explanation) && text(value.naConditions) && text(value.basisNote) && nullableText(value.priceAsOf);
}

// TypeScript는 실제 HTTP JSON을 검사하지 않으므로 화면이 순회하는 구조와 숫자 문자열을 경계에서 확인합니다.
export const contract = {
  search: (value: unknown) => Array.isArray(value) && value.every(company),
  detail: (value: unknown) => object(value) && company(value) && text(value.industryName) && text(value.industryCode)
    && Number.isInteger(value.fiscalMonth) && (value.hasPreferredShares === null || typeof value.hasPreferredShares === 'boolean')
    && object(value.price) && nullableText(value.price.closePrice) && nullableText(value.price.marketCap)
    && text(value.price.currency) && status(value.price.status),
  financials: (value: unknown) => object(value) && company(value.company) && status(value.status) && Array.isArray(value.years)
    && value.years.every(y => object(y) && Number.isInteger(y.fiscalYear) && basis(y.basis) && text(y.currency)
      && Array.isArray(y.accounts) && y.accounts.every(a => object(a) && text(a.code) && text(a.label) && nullableText(a.value)
        && text(a.unit) && status(a.status) && nullableText(a.reason)
        && (a.original === null || (object(a.original) && text(a.original.accountName) && text(a.original.accountId))))),
  metrics: (value: unknown) => object(value) && company(value.company) && status(value.status)
    && (value.fiscalYear === null || Number.isInteger(value.fiscalYear)) && (value.basis === null || basis(value.basis))
    && Array.isArray(value.metrics) && value.metrics.every(metric),
  profitability: (value: unknown) => object(value) && company(value.company) && status(value.status) && Array.isArray(value.years)
    && value.years.every(y => object(y) && Number.isInteger(y.fiscalYear) && basis(y.basis)
      && Array.isArray(y.metrics) && y.metrics.every(metric)),
};
