# Stock Insight 프론트엔드 — 0.6.0

React·TypeScript 화면에서 기업을 검색하고 기업개요·재무정보·현금흐름·투자지표 4개 탭을 조회합니다. 금액과 지표는 Spring Boot API에서 받습니다. 프론트엔드는 표시 단위를 바꾸며 금융지표를 계산하지 않습니다.

현재 버전은 프론트엔드·백엔드 모두 **0.6.0**입니다. **Phase 6 통합·품질 검증을 완료했습니다.** 프로덕션 화면의 오류·결측·지연·복구·390px과 로컬 API 보안 경계를 확인했습니다. [Phase 6 보고서](docs/phase-6-verification.md)에 결과와 알려진 제한을 기록했습니다. 4탭의 이전 검증은 [Phase 5 보고서](docs/phase-5-verification.md)에 있습니다. Phase 4의 사용자 공시 원문 대조는 미확인 상태입니다.

Phase7에서 문서를 현재 구현에 맞춰 정리했습니다. [프로젝트 README](../README.md)에서 처음 준비·버전·문서 목록을, [설계 결정](../docs/design-decisions.md)·[포트폴리오 설명](../docs/portfolio.md)·[검증 안내](../docs/verification.md)에서 이유와 재현 근거를 확인할 수 있습니다. 앱 코드는 변경하지 않았습니다.

## PowerShell에서 실행

전체 구조·상태·계약·검증 근거는 [소프트웨어 설계 명세서(SDD)](../docs/SDD.md), IntelliJ·VS Code로 각각 실행하는 방법은 [IDE 개발 안내](../docs/ide-development-guide.md)를 참조하세요.

**서버 시작·종료·PC 재부팅 후 다시 켜는 순서는 [PowerShell 실행·종료 안내](docs/local-run-guide.md)를 먼저 보세요.** 실제 기업 조회는 REAL 명령 하나·PowerShell 창 하나면 됩니다. Mock+Real은2개, QA까지 모두 켜면3개 창이 필요합니다. 각 명령이 DB→백엔드→화면을 자동으로 시작하므로 서로 실행 순서는 독립적입니다. 종료는 각 창에서 Enter입니다.

현재 PC에 설치된 MySQL 8.0·Java 21·Node를 사용하며 Docker가 필요하지 않습니다. 각 스크립트는 자체 DB 경로와 프로세스만 관리합니다. 사용 중인 포트를 발견하면 중단합니다. 기존 서버를 종료한 뒤 다시 실행해야 새 JAR이 적용됩니다.

### Phase 6 프로덕션·오류 처리 검증

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-quality-check.ps1"
```

READY 후 [검증 제어 화면](http://127.0.0.1:5206/__quality)에서 시나리오를 선택합니다. 프로덕션 dist·0.6.0 백엔드·전용 DB를 실행하며 API 키가 필요하지 않습니다. 백엔드18096·MySQL13396, DB 경로는 `work/phase6-browser/mysql-data`입니다. 실패는 검증 프록시에서만 주입하며 일반 앱과 DB 원본은 바꾸지 않습니다. 코드 변경 후에는 먼저 `npm.cmd run build`로 dist를 갱신합니다.

### Phase 5 전용 실행 — 권장

이전 단계 서버의 포트·DB 파일과 충돌하지 않도록 별도 경로를 사용합니다. 아래 명령 중 필요한 모드만 실행합니다. 실제 기업 조회는 Real 하나면 되고, Mock과Real을 함께 쓸 때는 서로 다른 PowerShell 창에서 실행합니다. 파일 이름의 phase5와 관계없이 현재0.6.0 백엔드를 사용합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-phase5-local.ps1" -Mode Mock
```

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-phase5-local.ps1" -Mode Real
```

| 모드 | 화면 | 백엔드 | MySQL | 별도 DB 경로 |
|---|---|---|---|---|
| Mock | [5194](http://127.0.0.1:5194) | 18094 | 13394 | `work/phase5-browser-mock/mysql-data` |
| Real | [5195](http://127.0.0.1:5195) | 18095 | 13395 | `work/phase5-browser-real/mysql-data` |

최초 DB 초기화 후 READY가 나오면 창을 열어 둡니다. `ibdata1 must be writable`는 같은 DB 파일을 다른 MySQL이 사용하거나 쓰기 권한이 없을 때 발생할 수 있습니다. 기존 파일을 삭제하지 말고 이전 실행을 정상 종료하거나 별도 인스턴스 경로를 사용하세요. 다음의 기본 실행은 이전 단계와 같은 포트를 유지합니다.

### 실제 기업

백엔드의 `.env.real`에 저장한 DART·FSC 키를 사용합니다. 키를 화면이나 로그로 출력하지 않습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-real-local.ps1"
```

`READY (REAL)`이 나오면 [실제 데이터 화면](http://127.0.0.1:5175)을 열고 기아 또는 000270을 검색해 선택합니다. 첫 조회는 공시 수집 때문에 시간이 걸릴 수 있습니다. 연결 실패 시 가상 수치로 대체하지 않습니다.

기본 화면 포트는 5175, 백엔드는 8081, MySQL은 13307입니다. 백엔드·DB 포트는 `.env.real`에서 읽으며 매개변수로 변경할 수 있습니다. DB는 작업 폴더의 `work/real-local/mysql-data`에 보존합니다.

### 가상 사례

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-browser-check.ps1"
```

`READY` 후 [가상 데이터 화면](http://127.0.0.1:5174)을 엽니다. 화면 5174, 백엔드 18081, MySQL 13336이며 DB는 `work/browser-check-local/mysql-data`에 보존합니다.

| 종목코드 | 확인할 사례 |
|---|---|
| 990001 | 정상 5개년, 매출 1,800억 원, 영업이익률 10%, ROE 11% |
| 990002 | 적자·음수 EPS, PER N/A |
| 990003 | 우선주 발행으로 BPS·PBR N/A |
| 990004 | 3월 결산, 확보 2개년, 연결·별도 변경, 누락 계정·시세 |
| 990005 | 금융업 미지원 안내, 이전 기업 카드 제거 |

사용하는 동안 PowerShell 창을 열어 두세요. **종료하려면 Enter**를 누릅니다. 스크립트가 시작한 프로세스만 중지하고 환경변수를 복원합니다. `-CheckOnly`는 서버 시작 확인 후 즉시 종료하는 옵션이며 브라우저 사용 중에는 넣지 않습니다. 자동 실행 환경의 localhost가 PC 브라우저에서 접근되지 않을 때는 PC PowerShell에서 실행해야 합니다.

### 일반 개발 실행

백엔드는 [실행 안내](../stock-insight-backend/README.md)에 따라 먼저 시작합니다. 프론트엔드는 별도 터미널에서 실행합니다.

```powershell
Set-Location 'C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend'
npm.cmd ci
npm.cmd run dev
```

기본 [개발 화면](http://127.0.0.1:5173)의 `/api` 요청을 Vite가 백엔드 8080으로 전달합니다. 다른 포트는 `.env.local`의 `API_PROXY_TARGET`을 수정한 뒤 재시작합니다.

## 화면과 데이터 기준

- **기업개요:** 업종·결산월·우선주 여부·종가·시가총액과 각각의 기준일·출처.
- **재무정보:** 최근 확보 연간 자료, 매출 막대와 영업·순이익률 선. 오른쪽 보조축은 %. 손익계산서/재무상태표 및 차트/표 전환.
- **현금흐름:** 영업·투자·재무활동 현금흐름 차트/표와 의미 설명.
- **투자지표:** PER·PBR·ROE·ROA·EPS·BPS·부채비율 카드/표. 공식·사용 계정·연도·N/A 조건 툴팁. ROE·ROA는 기말 기준.

차트는 같은 연결/별도 기준만 표시합니다. 다른 기준이나 누락 연도는 빈 구간이며 선으로 연결하지 않습니다. 표는 각 연도의 기준을 함께 표시합니다. N/A와 실제 0을 구분하고 음수를 보존합니다. 금액의 원본 문자열은 표·툴팁에 보존하며 SVG 좌표만 Number로 변환합니다.

양수·음수는 빨강·파랑과 부호·텍스트를 함께 사용합니다. 정보 버튼은 마우스 올리기, 클릭, 키보드 Enter로 열고 Escape 또는 바깥 클릭으로 닫습니다. 탭은 방향키·Home·End를 지원합니다. 좁은 화면에서 표는 자체 영역 안에서 가로로 이동합니다.

출처 패널에는 연도별 기간·연결/별도·공시일·수집 시각·접수번호를 표시합니다. 실제 접수번호는 DART 공시로 연결됩니다. 주가 기준일과 재무 연도는 별도로 표시하며 저장 자료가 최신 거래일까지 갱신됐다고 가정하지 않습니다.

금융 공식·계정·기준은 [금융 정책5.3](../stock-insight-backend/docs/financial-policy.md#53-지표-기준표)을 참조합니다. ROE는 지배주주·기말, ROA는 기말, EPS는 공시 보통주 기본주당이익, BPS는 확인된 보통주 기준입니다. 금융업·분기/반기/TTM은 미지원이며 우선주 BPS·PBR과 미확정 공시 EPS의 EPS·PER은 N/A입니다. 주식 분할·무상증자에 따른 과거 EPS 단절과 재작성 전기값을 자동 보정하지 않습니다. [정책5.6](../stock-insight-backend/docs/financial-policy.md#56-알려진-제한)에 전체 제한이 있습니다.

## 구조와 설계

```text
src/
  App.tsx                       대시보드 진입점
  DashboardApp.tsx              검색·선택·4종 API 요청·상태 검증
  components/Dashboard.tsx       4개 탭·카드·표·출처
  components/FinancialChart.tsx  Recharts, 필요할 때만 로드
  components/InfoTooltip.tsx     클릭·포커스·마우스 툴팁
  lib/api.ts                    API 요청·오류·AbortController
  lib/types.ts                  API 응답 타입
  lib/dashboard-view.ts         문자열 표시·차트 자료 구성
  lib/finance-view.ts           기간·금액 표시 공통 함수
```

기업 선택 시 재무·최신 지표·연도별 이익률을 병렬 조회합니다. 첫 실제 수집에서 우선주 여부가 갱신되므로 기본정보는 재무 요청이 끝난 뒤 읽습니다. 이전 요청은 취소하고 기업·모드·연도·연결/별도·공시 접수번호·기간이 맞는지 검사합니다. 지표 요청만 실패하면 계정 화면은 유지하고 투자지표에서 오류와 재시도 버튼을 제공합니다.

Phase 6에서는 실제 HTTP JSON의 메타·배열·금액 문자열 구조도 검사합니다. 잘못된 지표 구조는 부분 실패로 처리하고 최초 REAL 오류의 모드를 보존합니다. 로컬 API는 외부 Host·Origin·cross-site 요청을403으로 거부하며 CORS 범위를 확대하지 않습니다.

연도별 이익률은 새 백엔드 `metrics/profitability` API가 기존 BigDecimal 계산 엔진으로 산출합니다. 계산 정책과 DB 스키마는 변경하지 않았습니다. Recharts는 복합 차트·반응형·접근성을 제공하므로 선택했습니다. 번들 크기가 증가해 차트 탭을 열 때 따로 로드합니다. 단순 표가 대안이며 모든 차트에 표 보기를 제공합니다.

## 버전·검증

| 항목 | 고정·검증 버전 |
|---|---|
| Node.js | 24.19.0 |
| React / React DOM / react-is | 19.3.1 |
| Recharts | 3.10.1 |
| Vite / React 플러그인 | 8.3.8 / 6.3.0 |
| TypeScript | 7.0.2 |
| Tailwind / Vite 플러그인 | 4.3.5 / 4.3.5 |
| Lucide React | 1.73.0 |

```powershell
npm.cmd test
npm.cmd run build
npm.cmd run preview
```

자동 테스트 **21개**, TypeScript 검사·프로덕션 빌드를 실행해 통과했습니다. 첫 수집 순서·잘못된 지표 구조·REAL 오류 모드·취소·숫자 문자열을 회귀 검사합니다. 현재 npm audit 취약점은0개입니다. 백엔드 전체 **217개**, 패키지 HTTP **17개**, 실제 브라우저 **15개 시나리오**도 통과했습니다. 차트 번들은 따로 분리되며 Vite preview는 로컬 확인용입니다.

[Recharts 설치](https://recharts.github.io/en-US/guide/installation/)·[접근성](https://github.com/recharts/recharts/wiki/Recharts-and-accessibility) 문서를 참고했습니다. 상세 실행 증거와 미검증 항목은 [Phase 5 보고서](docs/phase-5-verification.md), 이전 화면의 검증 기록은 [Phase 2 보고서](docs/verification.md)에 있습니다.

## 직접 확인·학습

1. 990001의 4개 탭에서 카드/표·차트/표를 바꾸고 값과 단위를 비교합니다.
2. 990002 손실과 990003 BPS·PBR N/A 사유를 확인합니다.
3. 990004의 차트 기준을 변경해 서로 다른 재무 기준이 한 선으로 이어지지 않는지 확인합니다.
4. 실제 기아·롯데케미칼·삼성전자를 조회하고 연도·출처·종가 기준일을 확인합니다.
5. 사용자 공시 원문 대조는 [별도 대조표](../stock-insight-backend/docs/live-verification-table.md)에 남겨 주세요.

직접 해볼 것: 990004의 2024년(별도)·2025년(연결)을 하나의 선으로 연결하면 왜 오해가 생기는지 설명해 보세요. 힌트는 비교 대상의 범위입니다.

면접 질문과 답변 요점:

1. 이익률을 왜 프론트엔드에서 계산하지 않았나요? — BigDecimal 계산과 N/A 기준을 백엔드 한 곳에서 관리하기 위해서입니다.
2. JSON 금액 문자열과 차트 숫자를 왜 구분하나요? — 원본의 정밀도를 유지하며, 그래프 좌표에만 근사 숫자를 쓰기 위해서입니다.
3. 차트를 늦게 로드하는 이유는 무엇인가요? — 첫 검색·요약 화면에서 사용하지 않는 차트 코드의 초기 다운로드를 줄이기 위해서입니다.

커밋 제안: `feat: add four-tab annual financial dashboard and profitability history API`

Phase6의 [수정·알려진 제한·학습 안내](docs/phase-6-verification.md)와 Phase7의 [문서화 보고](../docs/phase-7-documentation.md)를 확인하세요. 사용자 공시 원문 대조는 별도로 남아 있습니다.
