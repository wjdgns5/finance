import { Bar, BarChart, CartesianGrid, Cell, ComposedChart, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { decimalText, metricDisplay, moneyDisplay } from '../lib/dashboard-view.ts';
import { basisName } from '../lib/finance-view.ts';
import type { yearSeries } from '../lib/dashboard-view.ts';

type Row = ReturnType<typeof yearSeries>[number];
type Kind = 'income' | 'balance' | 'cash';
const compact = (value: number) => new Intl.NumberFormat('ko-KR', { notation: 'compact', maximumFractionDigits: 1 }).format(value);

function ChartTooltip({ active, payload, kind }: { active?: boolean; payload?: readonly { payload?: Row }[]; kind: Kind }) {
  const row = payload?.[0]?.payload;
  if (!active || !row) return null;
  const codes = kind === 'income' ? ['REVENUE'] : kind === 'balance'
    ? ['TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY']
    : ['OPERATING_CASH_FLOW', 'INVESTING_CASH_FLOW', 'FINANCING_CASH_FLOW'];
  const labels: Record<string, string> = { REVENUE: '매출액', TOTAL_ASSETS: '자산', TOTAL_LIABILITIES: '부채', TOTAL_EQUITY: '자본', OPERATING_CASH_FLOW: '영업활동', INVESTING_CASH_FLOW: '투자활동', FINANCING_CASH_FLOW: '재무활동' };
  return <div className="chart-tooltip"><strong>{row.fiscalYear}년 · {row.year ? basisName(row.year.basis) : '해당 기준 자료 없음'}</strong>
    {codes.map(code => { const money = moneyDisplay(row.year, code); return <p key={code}><span>{labels[code]}</span><b>{money ? `${money.eok}억 원` : 'N/A'}</b></p>; })}
    {kind === 'income' && [row.op, row.net].map((metric, index) => { const value = metricDisplay(metric); return <p key={index}><span>{index === 0 ? '영업이익률' : '순이익률'}</span><b className={value.tone}>{value.text}{value.unit}</b></p>; })}
  </div>;
}

export default function FinancialChart({ rows, kind }: { rows: Row[]; kind: Kind }) {
  const keys = kind === 'income' ? ['revenue'] : kind === 'balance' ? ['assets', 'liabilities', 'equity'] : ['operatingCash', 'investingCash', 'financingCash'];
  const hasValues = rows.some(row => keys.some(key => row[key as keyof Row] !== null && row[key as keyof Row] !== undefined));
  if (!hasValues) return <div className="chart-empty" role="status">이 기준에서 차트로 표시할 수 있는 자료가 없습니다. 표에서 N/A 사유를 확인하세요.</div>;
  const common = <><CartesianGrid stroke="#e9edf4" strokeDasharray="3 4" vertical={false} />
    <XAxis dataKey="fiscalYear" tickLine={false} axisLine={false} tick={{ fill: '#7b8799', fontSize: 11 }} tickFormatter={year => `${year}년`} />
    <YAxis yAxisId="amount" tickFormatter={compact} tickLine={false} axisLine={false} width={66} tick={{ fill: '#7b8799', fontSize: 10 }} />
    <ReferenceLine yAxisId="amount" y={0} stroke="#bac5d5" />
    <Tooltip content={<ChartTooltip kind={kind} />} cursor={{ fill: '#f0f4fb', fillOpacity: 0.6 }} />
  </>;
  return <figure className="financial-chart" aria-label={kind === 'income' ? '최근 연간 매출액과 이익률 차트' : kind === 'balance' ? '최근 연간 자산·부채·자본 차트' : '최근 연간 현금흐름 차트'}>
    <div className="chart-axis-labels"><span>금액 · 억 원</span>{kind === 'income' && <span>오른쪽 보조축 · 이익률 %</span>}</div>
    <div className="chart-canvas">
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        {kind === 'income' ? <ComposedChart data={rows} margin={{ top: 12, right: 8, left: 0, bottom: 8 }} accessibilityLayer>
          {common}
          <YAxis yAxisId="ratio" orientation="right" tickFormatter={value => `${value}%`} tickLine={false} axisLine={false} width={48} tick={{ fill: '#9e7f79', fontSize: 10 }} />
          <Bar yAxisId="amount" dataKey="revenue" name="매출액" fill="#7893e5" maxBarSize={44} radius={[4, 4, 0, 0]} isAnimationActive={false} />
          <Line yAxisId="ratio" dataKey="operatingMargin" name="영업이익률" stroke="#e17a67" strokeWidth={2.5} dot={{ r: 3.5 }} connectNulls={false} isAnimationActive={false} />
          <Line yAxisId="ratio" dataKey="netMargin" name="순이익률" stroke="#688e82" strokeWidth={2.5} dot={{ r: 3.5 }} connectNulls={false} isAnimationActive={false} />
        </ComposedChart> : <BarChart data={rows} margin={{ top: 12, right: 12, left: 0, bottom: 8 }} accessibilityLayer barGap={5}>
          {common}
          {(kind === 'balance' ? ['assets', 'liabilities', 'equity'] : ['operatingCash', 'investingCash', 'financingCash']).map((key, index) => <Bar key={key} yAxisId="amount" dataKey={key} name={key} maxBarSize={28} radius={[3, 3, 0, 0]} isAnimationActive={false} fill={['#7893e5', '#a8b7d4', '#77a395'][index]}>
            {kind === 'cash' && rows.map((row, point) => { const value = row[key as keyof Row]; return <Cell key={point} fill={typeof value === 'number' && value < 0 ? '#4b75d3' : '#d77c73'} />; })}
          </Bar>)}
        </BarChart>}
      </ResponsiveContainer>
    </div>
    <figcaption className="chart-caption">{kind === 'income' ? <><span><i style={{ background: '#7893e5' }} />매출액</span><span><i style={{ background: '#e17a67' }} />영업이익률</span><span><i style={{ background: '#688e82' }} />순이익률</span></> : kind === 'balance' ? <><span><i style={{ background: '#7893e5' }} />자산</span><span><i style={{ background: '#a8b7d4' }} />부채</span><span><i style={{ background: '#77a395' }} />자본</span></> : <><span>그룹 순서: 영업 · 투자 · 재무</span><span className="positive">+ 현금 유입</span><span className="negative">− 현금 유출</span></>}</figcaption>
    <p className="chart-footnote">빈 구간은 자료 없음이며 실제 0과 다릅니다. 차트 좌표는 표시용 근사값이고 원본 수치는 표에서 확인할 수 있습니다.</p>
  </figure>;
}
