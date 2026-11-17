import type { ApiEnvelope, ApiMeta, CompanyDetail, CompanySummary, Financials, Metrics, ProfitabilityHistory } from './types.ts';
import { contract, validMeta } from './api-contract.ts';

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly meta: ApiMeta | null;

  constructor(message: string, code = 'NETWORK_ERROR', status = 0, meta: ApiMeta | null = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.meta = meta;
  }
}

async function request<T>(path: string, signal: AbortSignal, validData: (value: unknown) => boolean): Promise<ApiEnvelope<T>> {
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, { signal, headers: { Accept: 'application/json' } });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new ApiError('데이터 서버에 연결할 수 없습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요.');
  }
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new ApiError('데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'INVALID_RESPONSE', response.status);
  }
  const envelope = body as { data?: T; meta?: ApiMeta; error?: { code?: string; message?: string } | null } | null;
  if (!response.ok || envelope?.error) {
    const error = envelope?.error;
    throw new ApiError(error?.message || '요청을 처리하지 못했습니다. 다시 시도해 주세요.',
      error?.code || 'REQUEST_FAILED', response.status, validMeta(envelope?.meta) ? envelope.meta : null);
  }
  if (!envelope || !validMeta(envelope.meta) || !validData(envelope.data)) {
    throw new ApiError('데이터 형식을 확인할 수 없습니다. 다시 시도해 주세요.', 'INVALID_RESPONSE', response.status);
  }
  return envelope as ApiEnvelope<T>;
}

export const api = {
  search(query: string, signal: AbortSignal) {
    return request<CompanySummary[]>(`/companies?query=${encodeURIComponent(query)}&limit=20`, signal, contract.search);
  },
  company(stockCode: string, signal: AbortSignal) {
    return request<CompanyDetail>(`/companies/${encodeURIComponent(stockCode)}`, signal, contract.detail);
  },
  financials(stockCode: string, signal: AbortSignal) {
    return request<Financials>(`/companies/${encodeURIComponent(stockCode)}/financials?years=5`, signal, contract.financials);
  },
  metrics(stockCode: string, signal: AbortSignal) {
    return request<Metrics>(`/companies/${encodeURIComponent(stockCode)}/metrics`, signal, contract.metrics);
  },
  profitability(stockCode: string, signal: AbortSignal) {
    return request<ProfitabilityHistory>(`/companies/${encodeURIComponent(stockCode)}/metrics/profitability?years=5`, signal, contract.profitability);
  },
};

export function loadDashboard(stockCode: string, signal: AbortSignal) {
  const financials = api.financials(stockCode, signal);
  // 첫 REAL 수집은 기업의 우선주 여부도 갱신합니다. 기본정보는 그 작업이 끝난 뒤 읽습니다.
  // 재무 조회가 실패한 경우에도 기본정보를 받아 미지원 업종 안내에 사용할 수 있습니다.
  const detail = financials.then(() => api.company(stockCode, signal), () => api.company(stockCode, signal));
  return Promise.allSettled([detail, financials, api.metrics(stockCode, signal), api.profitability(stockCode, signal)] as const);
}
