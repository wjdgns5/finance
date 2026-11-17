import { ArrowUpRight, CalendarDays, CircleHelp, FileText, Layers3, Wallet } from 'lucide-react';
import { basisName, formatDate, formatWon } from '../lib/finance-view.ts';
import type { RevenueView } from '../lib/finance-view.ts';

export default function RevenueCard({ view }: { view: RevenueView }) {
  let amount: ReturnType<typeof formatWon> | null = null;
  let reason = view.account?.reason || '수집된 매출액이 없습니다.';
  if (view.account?.status === 'AVAILABLE' && view.account.value !== null) {
    if (view.year.currency !== 'KRW' || view.account.unit !== 'KRW') {
      reason = '금액 단위를 확인할 수 없어 표시하지 않습니다.';
    } else {
      try { amount = formatWon(view.account.value); }
      catch { reason = '매출액의 금액 형식을 확인할 수 없습니다.'; }
    }
  }
  return (
    <article className="revenue-card panel" aria-labelledby="revenue-title">
      <div className="card-topline">
        <span className="eyebrow"><Wallet size={16} strokeWidth={1.7} /> 수익의 규모</span>
        <span className="annual-tag">ANNUAL</span>
      </div>
      <div className="metric-label-row">
        <h3 id="revenue-title">매출액</h3>
        <details className="tooltip">
          <summary aria-label="매출액 설명"><CircleHelp size={16} /></summary>
          <p>기업이 한 회계연도 동안 제품과 서비스를 판매한 금액입니다. 매출액만으로 기업의 수익성이나 투자 가치를 판단할 수는 없습니다.</p>
        </details>
      </div>
      <div className="metric-value" aria-label={amount ? `매출액 ${amount.eok}억 원` : '매출액 산출 불가'}>
        <span>{amount?.eok ?? 'N/A'}</span>{amount && <span className="metric-unit">억 원</span>}
      </div>
      <p className="original-value">{amount ? `원 단위 ${amount.original}` : reason}</p>
      <div className="metric-context">
        <span><CalendarDays size={14} /> {view.year.fiscalYear} 사업연도</span>
        <span><Layers3 size={14} /> {basisName(view.year.basis)}</span>
      </div>
      <div className="metric-bottom"><span className="square-dot" />연간 사업보고서 기준<span className="metric-bottom-label">{formatDate(view.meta.periodEnd)} 결산</span></div>
    </article>
  );
}

export function RevenueGuide() {
  return (
    <aside className="reading-guide" id="reading-guide" aria-labelledby="guide-title">
      <div className="guide-icon"><FileText size={20} strokeWidth={1.7} /></div>
      <span className="eyebrow">처음 보는 재무정보</span>
      <h3 id="guide-title">매출액은<br />기업의 판매 규모예요.</h3>
      <p>제품과 서비스를 얼마나 판매했는지 보여주는 숫자입니다. 이익이 얼마 남았는지와는 다를 수 있어요.</p>
      <div className="guide-note"><ArrowUpRight size={16} /><span>숫자와 함께 <strong>기준 연도와 재무제표 종류</strong>를 확인해 보세요.</span></div>
    </aside>
  );
}
