import { lazy, Suspense, useState } from 'react';
import { ArrowDownRight, ArrowUpRight, BarChart3, Building2, ChartNoAxesCombined, ChevronRight, Database, FileText, Landmark, Layers3, RefreshCw, Table2, Wallet } from 'lucide-react';
import type { ApiEnvelope, CompanyDetail, Financials, FinancialYear, Metrics, MetricValue, ProfitabilityHistory, StatementBasis } from '../lib/types.ts';
import { ACCOUNT_LABELS, account, amountTone, decimalText, findMetric, metricDisplay, moneyDisplay, sourceName, yearSeries } from '../lib/dashboard-view.ts';
import { basisName, formatCollectedAt, formatDate, formatWon } from '../lib/finance-view.ts';
import InfoTooltip from './InfoTooltip.tsx';
const FinancialChart = lazy(() => import('./FinancialChart.tsx'));

interface Props {
  detail: ApiEnvelope<CompanyDetail>;
  financials: ApiEnvelope<Financials>;
  metrics: ApiEnvelope<Metrics> | null;
  profitability: ApiEnvelope<ProfitabilityHistory> | null;
  metricsError: string;
  profitabilityError: string;
  reload: () => void;
}
const tabs = [{ id: 'overview', label: '기업개요', Icon: Building2 }, { id: 'financials', label: '재무정보', Icon: BarChart3 },
  { id: 'cash', label: '현금흐름', Icon: Wallet }, { id: 'metrics', label: '투자지표', Icon: ChartNoAxesCombined }] as const;
const investmentCodes = ['PER', 'PBR', 'ROE', 'ROA', 'EPS', 'BPS', 'DEBT_RATIO'];
const metricLabels: Record<string, string> = { PER: 'PER', PBR: 'PBR', ROE: 'ROE', ROA: 'ROA', EPS: 'EPS', BPS: 'BPS', DEBT_RATIO: '부채비율' };
const descriptions: Record<string, string> = {
  REVENUE: '한 회계연도 동안 제품과 서비스를 판매한 금액입니다.',
  OPERATING_PROFIT: '매출에서 본업의 영업 비용을 차감한 이익입니다. 음수이면 영업손실입니다.',
  NET_INCOME: '비용과 세금 등을 반영한 최종 순이익입니다. 지배주주 귀속 순이익과 구분합니다.',
  OPERATING_CASH_FLOW: '제품 판매와 원재료·급여 지급 등 영업활동에서 들어오고 나간 현금입니다.',
  INVESTING_CASH_FLOW: '설비·투자자산의 취득과 처분 등 투자활동의 현금흐름입니다. 음수만으로 나쁜 상태라고 판단하지 않습니다.',
  FINANCING_CASH_FLOW: '차입·상환·주식 발행·배당 등 자금 조달과 반환의 현금흐름입니다.',
};

function MetricHelp({ metric, year, basis }: { metric: MetricValue; year: number | null; basis: StatementBasis | null }) {
  return <InfoTooltip label={metric.label}><span>{metric.explanation}</span><span><b>공식</b> {metric.formula}</span>
    <span><b>사용 계정</b> {metric.usedAccounts.map(code => ACCOUNT_LABELS[code] || code).join(', ')}</span>
    <span><b>기준</b> {year ?? '미확인'}년 · {basis ? basisName(basis) : '미확인'} · {metric.basisNote}</span>
    {metric.priceAsOf && <span><b>종가 기준일</b> {formatDate(metric.priceAsOf)}</span>}
    <span><b>N/A 조건</b> {metric.naConditions}</span>{metric.reason && <span><b>현재 사유</b> {metric.reason}</span>}</InfoTooltip>;
}

function MetricCard({ metric, code, year, basis }: { metric: MetricValue | null; code: string; year: number | null; basis: StatementBasis | null }) {
  const value = metricDisplay(metric);
  return <article className="investment-card"><div className="card-label"><h4>{metric?.label || metricLabels[code]}</h4>{metric && <MetricHelp metric={metric} year={year} basis={basis} />}</div>
    <div className={`investment-value ${value.tone}`}>{value.text}<small>{value.unit}</small></div>
    <p className={value.reason ? 'na-reason' : 'metric-explanation'}>{value.reason || metric?.explanation}</p>
    <div className="card-basis">{year ? `${year}년` : '연도 미확인'} · {basis === 'CFS' ? '연결' : basis === 'OFS' ? '별도' : '기준 미확인'}{(code === 'ROE' || code === 'ROA') && <b>기말 기준</b>}</div>
  </article>;
}

function MoneyCard({ year, code, growth }: { year: FinancialYear | undefined; code: string; growth?: MetricValue | null }) {
  const money = moneyDisplay(year, code);
  const raw = account(year, code);
  const delta = metricDisplay(growth);
  return <article className="summary-card"><div className="card-label"><h3>{ACCOUNT_LABELS[code]}</h3><InfoTooltip label={ACCOUNT_LABELS[code]}><span>{descriptions[code]}</span><span>원본 금액은 원 단위로 저장하고 화면에서 억 원으로 표시합니다.</span>{raw?.original && <span>원본 계정: {raw.original.accountName} · {raw.original.accountId}</span>}</InfoTooltip></div>
    <div className={`summary-amount ${amountTone(raw?.value)}`}>{money?.eok ?? 'N/A'}{money && <small>억 원</small>}</div>
    {growth ? <p className={`summary-sub ${delta.tone}`}>{delta.reason ? '전년 비교 N/A' : <>{delta.tone === 'negative' ? <ArrowDownRight size={13} /> : <ArrowUpRight size={13} />}{delta.text}{delta.unit} · 전년 대비</>}{delta.reason && <InfoTooltip label="매출액 증가율"><span>{delta.reason}</span><span>{growth.formula}</span></InfoTooltip>}</p>
      : <p className="summary-sub">{money ? (raw?.value?.startsWith('-') ? '손실 금액을 원래 부호로 표시합니다.' : `${year?.fiscalYear}년 ${year?.basis === 'CFS' ? '연결' : '별도'} 기준`) : '수집된 해당 계정이 없습니다.'}</p>}
    {money && <span className="exact-amount">{money.original}</span>}
  </article>;
}

function ViewSwitch({ value, onChange, cards = false }: { value: string; onChange: (value: 'chart' | 'table') => void; cards?: boolean }) {
  return <div className="view-switch" role="group" aria-label="표시 방식"><button type="button" aria-pressed={value === 'chart'} onClick={() => onChange('chart')}><BarChart3 size={14} />{cards ? '카드' : '차트'}</button><button type="button" aria-pressed={value === 'table'} onClick={() => onChange('table')}><Table2 size={14} />표</button></div>;
}

function AccountTable({ years, codes, profitability }: { years: FinancialYear[]; codes: string[]; profitability?: ProfitabilityHistory | null }) {
  return <div className="table-scroll" tabIndex={0} role="region" aria-label="연간 재무 수치 표"><table className="data-table"><caption>연간 수치 · 금액은 억 원, 이익률은 % · N/A는 미수집 또는 산출 불가</caption>
    <thead><tr><th scope="col">계정 / 단위</th>{years.map(year => <th scope="col" key={year.fiscalYear}>{year.fiscalYear}년<small>{year.basis === 'CFS' ? '연결' : '별도'}</small></th>)}</tr></thead>
    <tbody>{codes.map(code => <tr key={code}><th scope="row">{ACCOUNT_LABELS[code]}{descriptions[code] && <InfoTooltip label={ACCOUNT_LABELS[code]}><span>{descriptions[code]}</span></InfoTooltip>}</th>{years.map(year => {
      const money = moneyDisplay(year, code), raw = account(year, code);
      return <td key={year.fiscalYear} className={amountTone(raw?.value)}>{money ? <span title={money.original}>{money.eok}</span> : <span className="na-cell">N/A<InfoTooltip label={`${year.fiscalYear}년 ${ACCOUNT_LABELS[code]}`}><span>{year.accounts.find(a => a.code === code)?.reason || '확인된 원화 계정 값이 없습니다.'}</span></InfoTooltip></span>}</td>;
    })}</tr>)}
    {profitability && ['OPERATING_MARGIN', 'NET_MARGIN'].map(code => <tr key={code}><th scope="row">{code === 'OPERATING_MARGIN' ? '영업이익률 (%)' : '순이익률 (%)'}</th>{years.map(year => {
      const metric = findMetric(profitability.years.find(p => p.fiscalYear === year.fiscalYear && p.basis === year.basis)?.metrics, code);
      const value = metricDisplay(metric);
      return <td key={year.fiscalYear} className={value.tone}>{value.text}{value.unit}{value.reason && <InfoTooltip label={`${year.fiscalYear}년 이익률`}><span>{value.reason}</span></InfoTooltip>}</td>;
    })}</tr>)}</tbody>
  </table></div>;
}

function CompanyOverview({ detail, year, metricValues }: { detail: ApiEnvelope<CompanyDetail>; year: FinancialYear | undefined; metricValues: MetricValue[] }) {
  const company = detail.data, market = detail.meta.market;
  const validPrice = company.price.status === 'AVAILABLE' && company.price.currency === 'KRW';
  function price(value: string | null, cap = false) { if (!validPrice || value === null) return 'N/A'; try { return cap ? `${formatWon(value).eok}억 원` : `${decimalText(value)}원`; } catch { return 'N/A'; } }
  return <div className="overview-grid"><article className="overview-profile"><div className="section-title"><Landmark size={17} /><h3>기업 기본정보</h3></div><dl className="profile-list">
    <div><dt>시장 / 종목코드</dt><dd>{company.market} · {company.stockCode}</dd></div><div><dt>업종</dt><dd>{company.industryName}</dd></div><div><dt>결산월</dt><dd>{company.fiscalMonth}월</dd></div>
    <div><dt>재무 기준</dt><dd>{year ? `${year.fiscalYear}년 · ${basisName(year.basis)}` : '수집된 연간 자료 없음'}</dd></div>
    <div><dt>우선주</dt><dd>{company.hasPreferredShares === true ? '발행 확인' : company.hasPreferredShares === false ? '미발행 확인' : '확인되지 않음'}</dd></div>
    <div><dt>종가</dt><dd>{price(company.price.closePrice)}<small>{market ? `${formatDate(market.closePriceAsOf)} · ${sourceName(market.closePriceSource)}` : company.price.reason || '기준일 없음'}</small></dd></div>
    <div><dt>시가총액</dt><dd>{price(company.price.marketCap, true)}<small>{market?.marketCapAsOf ? `${formatDate(market.marketCapAsOf)} · ${sourceName(market.marketCapSource || '')}` : '확인된 기준일 없음'}</small></dd></div>
  </dl></article><article className="overview-reading"><span className="section-kicker">READ THE NUMBERS</span><h3>규모와 수익성을<br />함께 살펴보세요.</h3><p>매출은 기업이 판매한 규모, 이익률은 그 매출에서 이익이 차지하는 비율입니다. 한 가지 숫자만으로 투자 가치를 단정하지 않습니다.</p>
    <div className="reading-metrics">{['OPERATING_MARGIN', 'NET_MARGIN', 'ROE'].map(code => { const metric = findMetric(metricValues, code), value = metricDisplay(metric); return <div key={code}><span>{code === 'OPERATING_MARGIN' ? '영업이익률' : code === 'NET_MARGIN' ? '순이익률' : 'ROE · 기말 기준'}{metric && <MetricHelp metric={metric} year={year?.fiscalYear || null} basis={year?.basis || null} />}</span><strong className={value.tone}>{value.text}<small>{value.unit}</small></strong></div>; })}</div>
    <p className="reading-note">음수와 0도 실제 수치입니다. N/A는 사유를 확인해 주세요.</p>
  </article></div>;
}

export default function Dashboard(props: Props) {
  const { detail, financials, metrics, profitability, metricsError, profitabilityError, reload } = props;
  const years = [...financials.data.years].sort((a, b) => b.fiscalYear - a.fiscalYear), latest = years[0];
  const [tab, setTab] = useState<string>('overview');
  const [view, setView] = useState<'chart' | 'table'>('chart');
  const [cashView, setCashView] = useState<'chart' | 'table'>('chart');
  const [metricView, setMetricView] = useState<'chart' | 'table'>('chart');
  const [statement, setStatement] = useState<'income' | 'balance'>('income');
  const [basis, setBasis] = useState<StatementBasis>(latest?.basis || 'CFS');
  const bases = [...new Set(years.map(y => y.basis))];
  const rows = yearSeries(years, profitability?.data || null, basis);
  const metricValues = metrics?.data.metrics || [];
  const keyYear = metrics?.data.fiscalYear ?? latest?.fiscalYear ?? null;
  const keyBasis = metrics?.data.basis ?? latest?.basis ?? null;
  const stale = financials.meta.stale || detail.meta.stale || metrics?.meta.stale;
  const investment = <>{metricsError && <div className="inline-error" role="alert">{metricsError}<button type="button" onClick={reload}>다시 불러오기</button></div>}
    <div className="section-heading"><div><h3>주요 투자지표</h3><p>{keyYear || '미확인'} 사업연도 · {keyBasis ? basisName(keyBasis) : '기준 미확인'}</p></div><ViewSwitch value={metricView} onChange={setMetricView} cards /></div>
    <div className="metric-date-note"><InfoTooltip label="재무와 주가 기준일"><span>{metrics?.data.priceNote || '최근 사업연도 재무와 표시된 주가 기준일을 함께 확인하세요.'}</span></InfoTooltip>{keyYear ? `재무 ${keyYear}년` : '재무 연도 미확인'} · 종가 {formatDate(metrics?.meta.market?.closePriceAsOf)} · ROE·ROA 기말 기준</div>
    {metricView === 'chart' ? <div className="investment-grid">{investmentCodes.map(code => <MetricCard key={code} code={code} metric={findMetric(metricValues, code)} year={keyYear} basis={keyBasis} />)}</div>
      : <div className="table-scroll" tabIndex={0} role="region" aria-label="투자지표 표"><table className="data-table metrics-table"><caption>최신 확보 연간 사업연도 지표</caption><thead><tr><th>지표</th><th>값</th><th>기준</th><th>N/A 사유</th></tr></thead><tbody>{investmentCodes.map(code => { const metric = findMetric(metricValues, code), value = metricDisplay(metric); return <tr key={code}><th scope="row">{metric?.label || metricLabels[code]}{metric && <MetricHelp metric={metric} year={keyYear} basis={keyBasis} />}</th><td className={value.tone}>{value.text}{value.unit}</td><td>{keyYear || '미확인'}년{(code === 'ROE' || code === 'ROA') && ' · 기말'}</td><td className="reason-column">{value.reason || '—'}</td></tr>; })}</tbody></table></div>}
  </>;

  return <div className="dashboard-content">
    <div className="dashboard-topline"><span><Layers3 size={14} />{latest ? `${latest.fiscalYear} 사업연도 · ${basisName(latest.basis)}` : '연간 자료 없음'}</span><button type="button" className="text-button" onClick={reload}><RefreshCw size={13} />다시 불러오기</button></div>
    {stale && <div className="state-notice" role="status">최근 수집에 실패한 항목이 있습니다. 마지막 정상 자료의 기준일·수집 시각을 확인하세요.</div>}
    <div className="dashboard-summary"><MoneyCard year={latest} code="REVENUE" growth={findMetric(metricValues, 'REVENUE_GROWTH')} /><MoneyCard year={latest} code="OPERATING_PROFIT" /><MoneyCard year={latest} code="NET_INCOME" />
      <article className="summary-card summary-roe"><div className="card-label"><h3>ROE <span className="end-period-tag">기말 기준</span></h3>{findMetric(metricValues, 'ROE') && <MetricHelp metric={findMetric(metricValues, 'ROE')!} year={keyYear} basis={keyBasis} />}</div><div className={`summary-amount ${metricDisplay(findMetric(metricValues, 'ROE')).tone}`}>{metricDisplay(findMetric(metricValues, 'ROE')).text}<small>{metricDisplay(findMetric(metricValues, 'ROE')).unit}</small></div><p className="summary-sub">{metricDisplay(findMetric(metricValues, 'ROE')).reason || '지배주주 지분 대비 순이익 비율'}</p></article>
    </div>
    <div className="dashboard-panel panel"><div className="dashboard-tabs" role="tablist" aria-label="기업 분석 탭">{tabs.map(({ id, label, Icon }, index) => <button key={id} type="button" role="tab" id={`tab-${id}`} aria-selected={tab === id} aria-controls={`panel-${id}`} tabIndex={tab === id ? 0 : -1} onClick={() => setTab(id)} onKeyDown={event => {
      const next = event.key === 'ArrowRight' ? (index + 1) % tabs.length : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : null;
      if (next !== null) { event.preventDefault(); setTab(tabs[next].id); (event.currentTarget.parentElement?.children[next] as HTMLButtonElement)?.focus(); }
    }}><Icon size={16} />{label}</button>)}</div>
      <section className="tab-content" role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} tabIndex={0}>
        {tab === 'overview' && <CompanyOverview detail={detail} year={latest} metricValues={metricValues} />}
        {(tab === 'financials' || tab === 'cash') && <>
          <div className="section-heading"><div><h3>{tab === 'cash' ? '현금의 흐름' : '최근 연간 재무정보'}</h3><p>{years.length}개년 확보 · 금액은 억 원</p></div><ViewSwitch value={tab === 'cash' ? cashView : view} onChange={tab === 'cash' ? setCashView : setView} /></div>
          <div className="chart-controls">{tab === 'financials' && <div className="statement-switch" role="group" aria-label="재무제표 종류"><button type="button" aria-pressed={statement === 'income'} onClick={() => setStatement('income')}>손익계산서</button><button type="button" aria-pressed={statement === 'balance'} onClick={() => setStatement('balance')}>재무상태표</button></div>}
            <label className="basis-picker">차트 기준<select aria-label="차트 재무제표 기준" value={basis} onChange={event => setBasis(event.target.value as StatementBasis)}>{bases.map(value => <option key={value} value={value}>{basisName(value)}</option>)}</select></label>
          </div>
          {bases.length > 1 && <p className="basis-warning">연결·별도 기준이 바뀐 연도가 있습니다. 차트는 선택한 기준만 표시하며 다른 기준의 연도는 빈 구간으로 둡니다. 표에는 연도별 기준을 함께 표시합니다.</p>}
          {tab === 'financials' && profitabilityError && <p className="inline-error" role="status">이익률을 불러오지 못했습니다. 매출·재무 계정은 계속 확인할 수 있습니다.</p>}
          {(tab === 'cash' ? cashView : view) === 'chart' ? <Suspense fallback={<div className="chart-empty" role="status">차트를 준비하고 있습니다.</div>}><FinancialChart rows={rows} kind={tab === 'cash' ? 'cash' : statement} /></Suspense>
            : <AccountTable years={years} codes={tab === 'cash' ? ['OPERATING_CASH_FLOW', 'INVESTING_CASH_FLOW', 'FINANCING_CASH_FLOW'] : statement === 'income' ? ['REVENUE', 'OPERATING_PROFIT', 'NET_INCOME', 'PARENT_NET_INCOME'] : ['TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY', 'PARENT_EQUITY']} profitability={tab === 'financials' && statement === 'income' ? profitability?.data : null} />}
          {tab === 'cash' && <div className="cash-guides">{['OPERATING_CASH_FLOW', 'INVESTING_CASH_FLOW', 'FINANCING_CASH_FLOW'].map(code => <article key={code}><h4>{ACCOUNT_LABELS[code]}</h4><p>{descriptions[code]}</p></article>)}</div>}
        </>}
        {tab === 'metrics' && investment}
      </section>
    </div>
    {!years.length && <div className="state-notice">{financials.data.reason || '수집된 연간 재무제표가 없어 N/A로 표시합니다.'}</div>}
    <details className="source-details panel"><summary><span><Database size={16} />출처 · 기준일 · 공시 원본</span><ChevronRight size={16} /></summary><div className="source-details-content">
      <dl><div><dt>데이터 출처</dt><dd>{financials.meta.sources.map(sourceName).join(', ')}</dd></div><div><dt>종가 기준일</dt><dd>{formatDate(detail.meta.market?.closePriceAsOf)} · {detail.meta.market ? sourceName(detail.meta.market.closePriceSource) : '시세 없음'}</dd></div><div><dt>시가총액 기준일</dt><dd>{formatDate(detail.meta.market?.marketCapAsOf)}</dd></div><div><dt>수집 시각 (한국)</dt><dd>{formatCollectedAt(financials.meta.collectedAt)}</dd></div></dl>
      <div className="filing-list">{financials.meta.financialPeriods.map(period => <div key={period.fiscalYear}><span>{period.fiscalYear}년 · {period.basis === 'CFS' ? '연결' : '별도'}<small>{formatDate(period.periodStart)} ~ {formatDate(period.periodEnd)} · 공시 {formatDate(period.filedAt)}</small><small>수집 {formatCollectedAt(period.collectedAt)}</small></span>{/^\d{14}$/.test(period.receiptNo) ? <a href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${period.receiptNo}`} target="_blank" rel="noreferrer">{period.receiptNo} <FileText size={12} /></a> : <code>{period.receiptNo}</code>}</div>)}</div>
      <p>저장된 자료를 재사용합니다. 수집 실패 표시가 없어도 최신 거래일까지 갱신되었다는 뜻은 아닙니다.</p>
    </div></details>
  </div>;
}
