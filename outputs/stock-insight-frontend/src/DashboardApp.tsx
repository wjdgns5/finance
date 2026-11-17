import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, BarChart3, Building2, Check, ChevronRight, CircleAlert, CircleHelp, FileSearch, Home, LoaderCircle, RefreshCw, Search, ShieldCheck, Sparkles } from 'lucide-react';
import { api, ApiError, loadDashboard } from './lib/api.ts';
import { displayName, revenueView } from './lib/finance-view.ts';
import { samePeriods, sourceName } from './lib/dashboard-view.ts';
import type { ApiEnvelope, ApiMeta, CompanyDetail, CompanySummary, Financials, Metrics, ProfitabilityHistory } from './lib/types.ts';
import Dashboard from './components/Dashboard.tsx';

type Selection = { kind: 'empty' } | { kind: 'loading'; company: CompanySummary }
  | { kind: 'ready'; company: CompanySummary; detail: ApiEnvelope<CompanyDetail>; financials: ApiEnvelope<Financials>;
      metrics: ApiEnvelope<Metrics> | null; profitability: ApiEnvelope<ProfitabilityHistory> | null;
      metricsError: string; profitabilityError: string }
  | { kind: 'error' | 'unsupported'; company: CompanySummary; message: string; detail?: ApiEnvelope<CompanyDetail> };

const message = (error: unknown) => error instanceof ApiError ? error.message : '자료를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';

export default function DashboardApp() {
  const [query, setQuery] = useState('');
  const [companies, setCompanies] = useState<CompanySummary[]>([]);
  const [searching, setSearching] = useState(true);
  const [searchError, setSearchError] = useState('');
  const [searchedQuery, setSearchedQuery] = useState('');
  const [latestMeta, setMeta] = useState<ApiMeta | null>(null);
  const [selection, setSelection] = useState<Selection>({ kind: 'empty' });
  const searchAbort = useRef<AbortController | null>(null);
  const selectionAbort = useRef<AbortController | null>(null);

  const search = useCallback(async (keyword: string) => {
    searchAbort.current?.abort();
    const controller = new AbortController(); searchAbort.current = controller;
    setSearching(true); setSearchError(''); setSearchedQuery(keyword);
    try {
      let response = await api.search(keyword, controller.signal);
      if (controller.signal.aborted) return;
      // 첫 화면은 서버가 알려 준 모드로 선택합니다. REAL에서 가상 기업 목록을 복구하지 않습니다.
      if (keyword === '[가상]' && response.meta.mode === 'REAL') {
        setMeta(response.meta); setSearchedQuery('기아'); setQuery(current => current || '기아');
        response = await api.search('기아', controller.signal);
      }
      if (controller.signal.aborted) return;
      if (!Array.isArray(response.data)) throw new ApiError('기업 목록을 확인할 수 없습니다.', 'INVALID_RESPONSE');
      setCompanies(response.data); setMeta(response.meta);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof ApiError && error.meta) setMeta(error.meta);
      setCompanies([]); setSearchError(message(error));
    } finally { if (!controller.signal.aborted) setSearching(false); }
  }, []);

  useEffect(() => {
    void search('[가상]');
    return () => { searchAbort.current?.abort(); selectionAbort.current?.abort(); };
  }, [search]);

  async function selectCompany(company: CompanySummary) {
    selectionAbort.current?.abort();
    const controller = new AbortController(); selectionAbort.current = controller;
    setSelection({ kind: 'loading', company });
    const [detailResult, financialResult, metricResult, historyResult] = await loadDashboard(company.stockCode, controller.signal);
    if (controller.signal.aborted) return;
    const detail = detailResult.status === 'fulfilled' ? detailResult.value : undefined;
    if (detail) setMeta(detail.meta);
    if (financialResult.status === 'rejected') {
      const error = financialResult.reason;
      if (!detail && error instanceof ApiError && error.meta) setMeta(error.meta);
      setSelection({ kind: error instanceof ApiError && error.code === 'UNSUPPORTED_INDUSTRY' ? 'unsupported' : 'error',
        company, detail, message: message(error) }); return;
    }
    if (!detail) { setSelection({ kind: 'error', company, message: '기업 기본정보를 불러오지 못했습니다. 다시 시도해 주세요.' }); return; }
    try {
      const financials = financialResult.value;
      if (detail.data.stockCode !== company.stockCode || financials.data.company.stockCode !== company.stockCode
        || detail.meta.mode !== financials.meta.mode) throw new Error('Mismatched company');
      const latest = revenueView(financials);
      let metrics = metricResult.status === 'fulfilled' ? metricResult.value : null;
      let profitability = historyResult.status === 'fulfilled' ? historyResult.value : null;
      let metricsError = metricResult.status === 'rejected' ? message(metricResult.reason) : '';
      let profitabilityError = historyResult.status === 'rejected' ? message(historyResult.reason) : '';
      const comparisonYears = latest ? financials.data.years.filter(y => y.fiscalYear === latest.year.fiscalYear || y.fiscalYear === latest.year.fiscalYear - 1) : [];
      if (metrics && (metrics.data.company.stockCode !== company.stockCode || metrics.meta.mode !== financials.meta.mode
        || !samePeriods(financials.meta, metrics.meta, comparisonYears)
        || (latest && (metrics.data.fiscalYear !== latest.year.fiscalYear || metrics.data.basis !== latest.year.basis)))) {
        metrics = null; metricsError = '투자지표의 기업 또는 재무 기준이 일치하지 않습니다. 다시 불러와 주세요.';
      }
      if (profitability && (profitability.data.company.stockCode !== company.stockCode || !samePeriods(financials.meta, profitability.meta, financials.data.years))) {
        profitability = null; profitabilityError = '연도별 이익률의 기업 또는 데이터 모드가 일치하지 않습니다.';
      }
      setMeta(financials.meta);
      setSelection({ kind: 'ready', company, detail, financials, metrics, profitability, metricsError, profitabilityError });
    } catch {
      setSelection({ kind: 'error', company, detail, message: '재무정보의 기업 또는 기준 정보를 확인할 수 없습니다. 다시 시도해 주세요.' });
    }
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!query.trim()) { setSearchError('기업명 또는 종목코드를 입력해 주세요.'); return; }
    void search(query.trim());
  }
  const selected = selection.kind === 'empty' ? null : selection.company;
  const ready = selection.kind === 'ready' ? selection : null;
  const detail = 'detail' in selection ? selection.detail : undefined;
  const meta = ready?.financials.meta ?? detail?.meta ?? latestMeta;
  const restoreQuery = meta?.mode === 'REAL' ? '기아' : '[가상]';

  return <div className="app-shell">
    <a className="skip-link" href="#analysis-content">본문으로 바로가기</a>
    <header className="topbar"><div className="topbar-inner">
      <a className="brand" href="./" aria-label="Stock Insight 홈"><span className="brand-icon"><BarChart3 size={23} /></span><span>Stock<span className="brand-light">Insight</span><span className="brand-version">2.0</span></span></a>
      <span className="header-divider" /><span className="header-caption">숫자로 이해하는 기업</span>
      <div className="topbar-actions"><details className="header-help"><summary><CircleHelp size={16} />이용 안내</summary><div><strong>검색하고, 선택하고, 살펴보세요.</strong><p>요약 카드를 먼저 보고 네 개 탭에서 재무와 지표를 확인하세요. 설명 아이콘은 마우스·키보드·터치로 열 수 있습니다.</p></div></details>
        <span className={`mode-badge ${meta?.mode === 'REAL' ? 'real' : ''}`}><span />{meta ? `${meta.mode} 모드` : '데이터 확인 중'}</span></div>
    </div></header>
    <main className="main-container">
      <nav className="breadcrumb" aria-label="현재 위치"><Home size={14} /><ChevronRight size={13} /><span>기업분석 대시보드</span></nav>
      <div className="page-heading"><div><div className="section-kicker">COMPANY INSIGHT</div><h1>기업의 숫자, 한눈에.</h1><p>규모부터 수익성, 현금의 흐름까지 차근차근 살펴보세요.</p></div><span className="annual-indicator"><FileSearch size={15} />연간 사업보고서 기준</span></div>
      {meta?.mode === 'MOCK' && <div className="mock-notice"><span className="notice-icon"><Sparkles size={16} /></span><p><strong>가상 데이터로 살펴보세요.</strong> 기업과 수치는 학습용 예시입니다. 실제 공시 데이터와 구분해 주세요.</p><span className="notice-label">SAMPLE DATA</span></div>}
      <div className="workspace-grid">
        <aside className="search-panel panel" aria-labelledby="search-title"><div className="panel-heading"><span className="heading-icon"><Search size={17} /></span><h2 id="search-title">기업 찾기</h2></div>
          <form className="search-form" onSubmit={submitSearch} role="search"><label className="sr-only" htmlFor="company-query">기업명 또는 종목코드</label><div className="search-input-wrap"><Search size={17} /><input id="company-query" name="companyQuery" value={query} onChange={event => { setQuery(event.target.value); if (searchError) setSearchError(''); }} placeholder="기업명 또는 종목코드" maxLength={100} autoComplete="off" /><kbd>↵</kbd></div><button className="primary-button" type="submit" disabled={searching}>{searching ? <LoaderCircle size={16} className="spin" /> : <Search size={16} />}검색하기</button></form>
          <div className="results-heading"><span>{latestMeta?.mode === 'MOCK' && searchedQuery === '[가상]' ? '살펴볼 가상 기업' : '검색 결과'}</span><span>{searching ? '조회 중' : searchError ? '확인 필요' : `${companies.length}개`}</span></div>
          <div className="results-region" aria-busy={searching}>{searching ? <div className="search-loading" role="status"><LoaderCircle size={22} className="spin" /><span>기업을 찾고 있어요.</span></div>
            : searchError ? <div className="search-empty" role="alert"><CircleAlert size={25} /><p>{searchError}</p><button className="text-button" onClick={() => void search(query.trim() || restoreQuery)}><RefreshCw size={13} />다시 시도</button></div>
              : companies.length === 0 ? <div className="search-empty" role="status"><FileSearch size={30} /><strong>검색 결과가 없어요.</strong><p>기업명이나 종목코드를<br />다시 확인해 주세요.</p><button className="text-button" onClick={() => { setQuery(restoreQuery === '[가상]' ? '' : restoreQuery); void search(restoreQuery); }}>{restoreQuery === '[가상]' ? '가상 기업 전체 보기' : '기아 검색하기'}<ArrowRight size={13} /></button></div>
                : <ul className="company-list">{companies.map((company, index) => <li key={company.stockCode}><button className={`company-row ${selected?.stockCode === company.stockCode ? 'selected' : ''}`} onClick={() => void selectCompany(company)} aria-pressed={selected?.stockCode === company.stockCode}><span className={`company-avatar avatar-${index % 4}`}><Building2 size={20} /></span><span className="company-row-text"><strong>{displayName(company.name)}</strong><span>{company.stockCode}<span className="dot-separator">·</span>{company.market}{!company.supported && <span className="unsupported-label">미지원 업종</span>}</span></span>{selected?.stockCode === company.stockCode ? <span className="selection-check"><Check size={14} /></span> : <ChevronRight size={16} className="row-chevron" />}</button></li>)}</ul>}</div>
          <div className="search-panel-note"><ShieldCheck size={15} /><span>원본 출처와 기준일을 확인하며<br />기업의 흐름을 살펴보세요.</span></div>
        </aside>
        <section className="analysis-column" id="analysis-content" aria-label="기업 재무 대시보드" aria-busy={selection.kind === 'loading'}>
          {!selected ? <div className="empty-analysis panel"><div className="empty-illustration"><span className="illustration-square square-back" /><span className="illustration-square square-front"><FileSearch size={43} strokeWidth={1.2} /></span><span className="illustration-dot" /></div><span className="section-kicker">YOUR FIRST INSIGHT</span><h2>궁금한 기업부터<br />찾아볼까요?</h2><p>기업을 선택하면 최근 연간 자료를 바탕으로<br />재무정보·현금흐름·투자지표를 보여드려요.</p><div className="empty-steps"><span><span>1</span>기업 검색</span><ChevronRight size={14} /><span><span>2</span>기업 선택</span><ChevronRight size={14} /><span><span>3</span>재무 확인</span></div></div> : <>
            <div className="company-heading"><div className="company-heading-main"><span className="selected-company-avatar"><Building2 size={26} /></span><div><div className="company-label"><span>{selected.market}</span><span>{selected.stockCode}</span>{meta?.mode === 'MOCK' && <span className="virtual-label">가상 기업</span>}</div><h2>{displayName(selected.name)}</h2></div></div><span className="overview-label"><span />연간 분석</span></div>
            {detail && <div className="company-facts"><span>업종<strong>{detail.data.industryName}</strong></span><span>결산월<strong>{detail.data.fiscalMonth}월</strong></span>{ready && <span>확보 자료<strong>{ready.financials.data.years.length}개년</strong></span>}</div>}
            {selection.kind === 'loading' ? <div className="analysis-loading panel" role="status"><LoaderCircle size={30} className="spin" /><h3>재무자료와 지표를 불러오고 있어요.</h3><p>처음 조회하는 실제 기업은 공시 수집 때문에 잠시 걸릴 수 있습니다.</p></div>
              : selection.kind === 'error' || selection.kind === 'unsupported' ? <div className="analysis-message panel" role="alert"><span className="message-icon"><CircleAlert size={27} /></span><h3>{selection.kind === 'unsupported' ? '아직 지원하지 않는 업종이에요.' : '자료를 불러오지 못했어요.'}</h3><p>{selection.message}</p>{selection.kind === 'error' && <button className="outline-button" onClick={() => void selectCompany(selection.company)}><RefreshCw size={15} />다시 불러오기</button>}</div>
                : ready && <Dashboard detail={ready.detail} financials={ready.financials} metrics={ready.metrics} profitability={ready.profitability} metricsError={ready.metricsError} profitabilityError={ready.profitabilityError} reload={() => void selectCompany(ready.company)} />}
          </>}
        </section>
      </div>
      <div className="bottom-guidance"><span><CircleHelp size={15} />처음이라면</span><p>숫자의 크기뿐 아니라 <strong>기준 연도·연결/별도·산출 조건</strong>을 함께 확인해 보세요.</p></div>
    </main>
    <footer className="page-footer"><div><span className="footer-brand">Stock Insight</span><span>본 서비스는 참고용 정보이며 투자 권유가 아닙니다.</span></div><span>데이터 출처: {meta ? meta.sources.map(sourceName).join(', ') || '조회 자료 없음' : '확인 중'}</span></footer>
  </div>;
}
