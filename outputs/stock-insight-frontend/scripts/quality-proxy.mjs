import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

// 테스트 서버에만 오류를 주입합니다. 앱 번들·DB의 원본 수치와 일반 실행 경로는 바꾸지 않습니다.
const { values } = parseArgs({ options: { port: { type: 'string', default: '5206' }, host: { type: 'string' }, strictPort: { type: 'boolean' } } });
const port = Number(values.port);
const upstream = process.env.API_PROXY_TARGET || 'http://127.0.0.1:18096';
if (!Number.isInteger(port) || port < 1024 || port > 65535 || !/^http:\/\/127\.0\.0\.1:\d+$/.test(upstream)) throw new Error('Local QA address required.');
const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist');
await stat(path.join(dist, 'index.html'));
const modes = {
  normal: '정상 통합 흐름', offline: 'API 연결 끊김', 'metrics-fail': '최신 지표만 실패', 'history-fail': '이익률 이력만 실패',
  'period-mismatch': '지표 공시 접수번호 불일치', 'malformed-metrics': '잘못된 지표 JSON 구조', stale: '마지막 정상 자료 표시',
  empty: '연간 재무자료 없음', 'slow-first': '한빛제조 응답 2.5초 지연', 'real-key-error': '첫 REAL 조회 인증키 오류',
};
const meta = mode => ({ mode, sources: [], collectedAt: null, financialPeriods: [], market: null, stale: false });
function json(res, status, body, headers = {}) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers }); res.end(JSON.stringify(body)); }
function failure(res, mode, code, message, status = 503) { json(res, status, { data: null, meta: meta(mode), error: { code, message, fields: [] } }); }
const type = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.woff2': 'font/woff2' };

const server = http.createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  try {
    if (!/^(127\.0\.0\.1|localhost):\d+$/.test(req.headers.host || '')) { res.writeHead(403); res.end(); return; }
    const url = new URL(req.url, `http://127.0.0.1:${port}`);
    const cookie = /(?:^|;\s*)SI_QUALITY_MODE=([a-z-]+)/.exec(req.headers.cookie || '')?.[1];
    const mode = Object.hasOwn(modes, cookie) ? cookie : 'normal';
    if (url.pathname === '/__quality/health') { json(res, 200, { status: 'READY', purpose: 'LOCAL_QA', mode }); return; }
    if (url.pathname === '/__quality' && req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end(`<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Phase 6 검증 제어</title><style>body{font:16px system-ui;max-width:680px;margin:50px auto;padding:20px;color:#24344e}select,button{font:inherit;padding:12px;margin:10px 0}p{line-height:1.8}</style><h1>Phase 6 검증 전용</h1><p>프로덕션 빌드와 실제 로컬 Mock API 사이에서 오류를 주입합니다. 표시되는 가상 수치는 백엔드에서 읽고 DB를 변경하지 않습니다.</p><form method="post" action="/__quality"><label for="mode">검증 시나리오</label><br><select name="mode" id="mode">${Object.entries(modes).map(([key, label]) => `<option value="${key}" ${key === mode ? 'selected' : ''}>${label}</option>`).join('')}</select><br><button>적용하고 앱 열기</button></form><a href="/">현재 시나리오 앱 열기</a></html>`); return;
    }
    if (url.pathname === '/__quality' && req.method === 'POST') {
      if (req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) { res.writeHead(403); res.end(); return; }
      let input = ''; for await (const chunk of req) { input += chunk; if (input.length > 1024) { res.writeHead(413); res.end(); return; } }
      const selected = new URLSearchParams(input).get('mode');
      if (!Object.hasOwn(modes, selected)) { res.writeHead(400); res.end(); return; }
      res.writeHead(303, { Location: '/', 'Set-Cookie': `SI_QUALITY_MODE=${selected}; Path=/; HttpOnly; SameSite=Strict`, 'Cache-Control': 'no-store' }); res.end(); return;
    }
    if (url.pathname.startsWith('/api/')) {
      if (req.method !== 'GET') { res.writeHead(405); res.end(); return; }
      if (mode === 'offline') { res.destroy(); return; }
      if (mode === 'real-key-error') { failure(res, 'REAL', 'DART_KEY_MISSING', 'DART_API_KEY를 설정해야 실제 데이터를 조회할 수 있습니다.'); return; }
      const latestMetrics = /\/metrics$/.test(url.pathname);
      const history = /\/metrics\/profitability$/.test(url.pathname);
      if (mode === 'metrics-fail' && latestMetrics) { failure(res, 'MOCK', 'QA_METRICS_FAILED', '검증용 지표 조회 실패입니다.'); return; }
      if (mode === 'history-fail' && history) { failure(res, 'MOCK', 'QA_HISTORY_TIMEOUT', '검증용 이익률 응답 시간 초과입니다.', 504); return; }
      const response = await fetch(upstream + url.pathname + url.search, { signal: AbortSignal.timeout(15000) });
      const body = await response.json();
      if (response.ok) {
        if (mode === 'stale') body.meta.stale = true;
        if (mode === 'period-mismatch' && latestMetrics) body.meta.financialPeriods[0].receiptNo += '-mismatch';
        if (mode === 'malformed-metrics' && latestMetrics) body.data.metrics = 'invalid-array';
        if (mode === 'empty' && (/\/financials$/.test(url.pathname) || latestMetrics || history)) {
          body.meta.financialPeriods = []; body.data.status = 'NOT_COLLECTED'; body.data.reason = '검증용: 수집된 연간 재무제표가 없습니다.';
          if (latestMetrics) { body.data.metrics = []; body.data.fiscalYear = null; body.data.basis = null; body.data.shares = null; }
          else body.data.years = [];
        }
      }
      if (mode === 'slow-first' && /\/companies\/990001(?:\/|$)/.test(url.pathname)) await new Promise(resolve => setTimeout(resolve, 2500));
      if (!res.destroyed) json(res, response.status, body);
      return;
    }
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
    const decoded = decodeURIComponent(url.pathname);
    if (decoded.split('/').some(part => part.startsWith('.') || part === '@fs')) { res.writeHead(403); res.end(); return; }
    let target = path.resolve(dist, '.' + decoded);
    if (target !== dist && !target.startsWith(dist + path.sep)) { res.writeHead(403); res.end(); return; }
    try { if (!(await stat(target)).isFile()) target = path.join(dist, 'index.html'); }
    catch { if (path.extname(target)) { res.writeHead(404); res.end(); return; } target = path.join(dist, 'index.html'); }
    let data = await readFile(target);
    if (path.extname(target) === '.html') data = Buffer.from(data.toString().replace('<body>', `<body><a href="/__quality" style="position:fixed;z-index:999;bottom:4px;right:4px;background:#24344e;color:white;padding:5px 8px;border-radius:4px;font:11px system-ui">검증 전용 · ${modes[mode]}</a>`));
    res.writeHead(200, { 'Content-Type': type[path.extname(target)] || 'application/octet-stream', 'Cache-Control': 'no-store' }); res.end(req.method === 'HEAD' ? undefined : data);
  } catch { if (!res.headersSent && !res.destroyed) failure(res, 'MOCK', 'QA_UPSTREAM_UNAVAILABLE', '검증 서버의 백엔드 연결을 확인하세요.', 502); else res.end(); }
});
server.listen(port, '127.0.0.1', () => console.log(`QUALITY READY: http://127.0.0.1:${port}/__quality`));
