import { test } from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, loadDashboard } from './api.ts';

const testMeta = { mode: 'REAL', sources: ['DART'], financialPeriods: [], market: null, collectedAt: null, stale: false };
const testCompany = { stockCode: '000270', name: '검증 기업', market: 'KOSPI', supported: true };
function emptyData(url: string, preferred: boolean | null = false) {
  if (url.includes('/financials?') || url.includes('/profitability?')) return { company: testCompany, status: 'NOT_COLLECTED', reason: null, years: [] };
  if (url.endsWith('/metrics')) return { company: testCompany, status: 'NOT_COLLECTED', fiscalYear: null, basis: null, metrics: [] };
  return { ...testCompany, industryCode: 'test', industryName: '검증 업종', fiscalMonth: 12, hasPreferredShares: preferred,
    price: { closePrice: null, marketCap: null, currency: 'KRW', status: 'NOT_COLLECTED', reason: null } };
}

test('검색어를 인코딩하고 AbortSignal을 요청에 전달한다', async (context) => {
  const controller = new AbortController();
  const meta = { mode: 'MOCK', sources: ['MOCK'], financialPeriods: [], market: null, collectedAt: null, stale: false };
  context.mock.method(globalThis, 'fetch', async (url: string, init: RequestInit) => {
    assert.equal(url, '/api/v1/companies?query=%ED%95%9C%EB%B9%9B%26&limit=20');
    assert.equal(init.signal, controller.signal);
    return Response.json({ data: [], meta, error: null });
  });
  assert.equal((await api.search('한빛&', controller.signal)).meta.mode, 'MOCK');
});

test('금융업 422 오류의 코드와 사유를 보존한다', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => Response.json({
    data: null, error: { code: 'UNSUPPORTED_INDUSTRY', message: '금융업 미지원' },
  }, { status: 422 }));
  await assert.rejects(api.financials('990005', new AbortController().signal), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, 'UNSUPPORTED_INDUSTRY');
    assert.equal(error.status, 422);
    return true;
  });
});

test('연결 실패를 가상 수치로 대체하지 않는다', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch'); });
  await assert.rejects(api.company('990001', new AbortController().signal), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, 'NETWORK_ERROR');
    return true;
  });
});

test('성공 HTTP라도 meta 모드가 없는 응답은 거부한다', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => Response.json({ data: [], meta: {} }));
  await assert.rejects(api.search('한빛', new AbortController().signal), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, 'INVALID_RESPONSE');
    return true;
  });
});

test('첫 실제 재무 수집이 우선주 정보를 갱신한 뒤 기업 기본정보를 읽는다', async (context) => {
  const meta = { mode: 'REAL', sources: ['DART'], financialPeriods: [], market: null, collectedAt: null, stale: false };
  const calls: string[] = [];
  let preferred: boolean | null = null;
  let finishCollection!: () => void;
  const collection = new Promise<void>(resolve => { finishCollection = resolve; });
  context.mock.method(globalThis, 'fetch', async (url: string) => {
    calls.push(url);
    if (url.includes('/financials?')) { await collection; preferred = false; }
    return Response.json({ data: emptyData(url, preferred), meta, error: null });
  });
  const pending = loadDashboard('000270', new AbortController().signal);
  assert.equal(calls.includes('/api/v1/companies/000270'), false);
  finishCollection();
  const [detail] = await pending;
  assert.equal(detail.status, 'fulfilled');
  if (detail.status === 'fulfilled') assert.equal(detail.value.data.hasPreferredShares, false);
  assert.equal(calls.at(-1), '/api/v1/companies/000270');
});

test('지표 응답 구조가 잘못되어도 재무와 기업 기본정보 조회는 유지한다', async (context) => {
  context.mock.method(globalThis, 'fetch', async (url: string) => Response.json({
    data: url.endsWith('/metrics') ? { ...emptyData(url), metrics: 'broken-array' } : emptyData(url), meta: testMeta, error: null,
  }));
  const [detail, financials, metrics, history] = await loadDashboard('000270', new AbortController().signal);
  assert.equal(detail.status, 'fulfilled');
  assert.equal(financials.status, 'fulfilled');
  assert.equal(history.status, 'fulfilled');
  assert.equal(metrics.status, 'rejected');
  if (metrics.status === 'rejected') assert.equal(metrics.reason.code, 'INVALID_RESPONSE');
});

test('금액이 JSON 숫자로 바뀌어 정밀도가 손실될 수 있는 응답은 거부한다', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => Response.json({
    data: { ...emptyData('/detail'), price: { closePrice: 95000, marketCap: null, currency: 'KRW', status: 'AVAILABLE' } },
    meta: testMeta, error: null,
  }));
  await assert.rejects(api.company('000270', new AbortController().signal), (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE');
});

test('첫 REAL 조회 실패의 데이터 모드를 보존해 가상 모드로 오인하지 않는다', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => Response.json({ data: null, meta: testMeta,
    error: { code: 'DART_KEY_MISSING', message: 'DART 키 필요' } }, { status: 503 }));
  await assert.rejects(api.search('[가상]', new AbortController().signal), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.meta?.mode, 'REAL');
    assert.equal(error.code, 'DART_KEY_MISSING');
    return true;
  });
});

test('취소된 요청의 늦은 실패를 일반 네트워크 오류로 바꾸지 않는다', async (context) => {
  const controller = new AbortController();
  const abortError = new DOMException('취소됨', 'AbortError');
  context.mock.method(globalThis, 'fetch', async () => { controller.abort(); throw abortError; });
  await assert.rejects(api.company('000270', controller.signal), (error: unknown) => error === abortError);
});
