# Stock Insight API — 0.6.0 현재 명세

실행 중인 백엔드의 `/swagger-ui.html`에서 API를 확인·호출하고 `/v3/api-docs` 또는 `/v3/api-docs.yaml`에서 OpenAPI를 받습니다. [Swagger 사용 안내](swagger-guide.md)에 포트·예시·명세 유지보수 절차가 있습니다. MOCK은5개, REAL은7개 업무 API를 표시합니다.

기본 경로: `http://127.0.0.1:8080/api/v1`.
이는 백엔드 단독 개발의 기본 주소입니다. 권장 실행 스크립트의 Mock API는18094, Real API는18095, QA API는18096입니다. 화면 접속 포트와 API 포트를 구분하며 [실행 안내](../../stock-insight-frontend/docs/local-run-guide.md)를 기준으로 사용합니다.
모든 응답은 `data`, `meta`, `error`의 같은 바깥 구조를 사용합니다.
성공이면 error=null, 실패면 data=null입니다. DB PK는 입력받지 않습니다.

| 요청 | 파라미터 | 성공 결과 |
|---|---|---|
| GET /companies | query 필수 1~100자, limit 기본 20 / 1~50 | 기업명 또는 종목코드 포함 검색 |
| GET /companies/{stockCode} | 숫자 6자리 | 기업 기본정보, 확보한 종가·시가총액 |
| GET /companies/{stockCode}/financials | years 기본 5 / 1~5 | 최신 확보 사업연도부터 최대 요청 개수 |
| GET /companies/{stockCode}/metrics | 숫자 6자리, 추가 파라미터 없음 | 최신 확보 연간 사업연도 10개 지표 |
| GET /companies/{stockCode}/metrics/profitability | years 기본 5 / 1~5 | 연도별 영업이익률·순이익률, 각 연도 연결/별도 기준 |
| POST /companies/{stockCode}/sync | REAL 전용, 숫자 6자리 | 재무·시세 재수집 결과 |
| GET /companies/{stockCode}/financials/{year}/source | REAL 전용, year 2015~2100, basis CFS/OFS | 저장된 원본 응답·기간·주식수·공시 정보 |

검색은 앞뒤 공백을 제거하고 %, _, !를 문자로 검색합니다. 검색어 내부 공백을 임의로 없애지 않습니다. 종목코드가 완전히 일치하면 우선 정렬합니다.
검색 결과가 없으면 200 + 빈 배열입니다. 이름 중복 가능성이 있으므로 사용자는 결과에서 종목을 선택합니다.
사업연도가 중간에 빠지면 확보한 연도만 반환합니다. 연속된 5개년을 확보했다고 추정하면 안 됩니다.

## 메타데이터

```json
{
  "data": {},
  "meta": {
    "mode": "MOCK",
    "sources": ["MOCK"],
    "collectedAt": "2026-11-17T00:00:00Z",
    "financialPeriods": [{
      "fiscalYear": 2025,
      "reportCode": "11011",
      "basis": "CFS",
      "receiptNo": "MOCK-990001-2025-CFS",
      "filedAt": "2026-03-20",
      "periodStart": "2025-01-01",
      "periodEnd": "2025-12-31",
      "source": "MOCK",
      "collectedAt": "2026-11-17T00:00:00Z"
    }],
    "market": {
      "closePriceAsOf": "2026-11-16",
      "closePriceSource": "MOCK",
      "marketCapAsOf": "2026-11-16",
      "marketCapSource": "MOCK",
      "collectedAt": "2026-11-17T00:00:00Z",
      "scope": "COMMON_STOCK"
    },
    "stale": false
  },
  "error": null
}
```

위 JSON의 data는 구조 설명용 빈 객체입니다. 실제 전체 응답은 [0.6.0 Mock](phase-6-responses.json)과 [0.6.0 REAL](phase-6-real-responses.json)에 실행 결과로 보존합니다.
모든 시각은 UTC이며 수집 시각은 실제 실행 때 달라집니다. 검색·기업 기본정보에는 financialPeriods가 빈 배열입니다.
시세가 없으면 market=null입니다. `meta.collectedAt`은 응답을 구성한 수집 시각의 최댓값이며,
재무제표별 수집 시각은 financialPeriods, 시세 수집 시각은 market을 기준으로 표시합니다.
동일 회계연도의 연결/별도 표시는 years와 financialPeriods 모두에서 확인할 수 있습니다.

## 데이터 상태와 숫자

금액·EPS·시가총액 및 주식수는 JSON 문자열입니다. 화면에서 계산 결과를 다시 산출하지 않습니다.

```json
{
  "code": "FINANCING_CASH_FLOW",
  "label": "재무활동 현금흐름",
  "value": "0.000000",
  "unit": "KRW",
  "status": "AVAILABLE",
  "reason": null,
  "original": {
    "accountId": "MOCK_FINANCING_CASH_FLOW",
    "accountName": "재무활동 현금흐름",
    "accountDetail": "가상 예시 계정",
    "amount": "0"
  }
}
```

누락값은 value=null, status=NOT_COLLECTED, reason에 한국어 사유를 반환합니다.
0 또는 음수 자체는 누락이 아닙니다. EPS가 음수이면 공시값으로 제공하고 PER 산출 가능 여부는 별도로 판정합니다.
DataStatus는 AVAILABLE / NOT_COLLECTED / NOT_APPLICABLE / UNSUPPORTED 하나의 enum을 공유합니다.
NOT_APPLICABLE은 지표 산출 불가 상태이며, 금융업 재무·지표 요청은 명확한 422 오류로 응답합니다.

## 계산 결과 API

`GET /companies/990001/metrics`는 최신 사업연도 지표 한 세트를 반환합니다.
계산 기준과 N/A 조건의 정의는 [금융지표 기준](financial-policy.md)을 참조합니다.
기존 financials API는 원본 계정과 과거 연도를 계속 제공합니다. 기존 응답 필드는 변경하지 않았습니다.

data 필드:

| 필드 | 의미 |
|---|---|
| company | 종목코드·이름·시장·지원 여부 |
| status / reason | 연간 자료 존재 여부. AVAILABLE이어도 각 지표는 N/A일 수 있음 |
| fiscalYear / basis / currency | 지표의 기준 사업연도·연결/별도·통화 |
| shares | 사용한 주식수 공시. 숫자는 문자열, asOf와 receiptNo로 추적 |
| priceNote | 재무 기준 연도와 주가 기준일이 다름을 안내 |
| metrics | REVENUE_GROWTH, OPERATING_MARGIN, NET_MARGIN, ROE, ROA, DEBT_RATIO, EPS, BPS, PER, PBR 순서 |

지표 하나의 예:

```json
{
  "code": "PBR",
  "label": "PBR",
  "value": "3.040000",
  "unit": "TIMES",
  "status": "AVAILABLE",
  "reasonCode": null,
  "reason": null,
  "formula": "종가 ÷ BPS",
  "usedAccounts": ["PARENT_EQUITY"],
  "explanation": "주가가 최근 사업연도 보통주 한 주당 순자산의 몇 배인지 보여줍니다.",
  "naConditions": "BPS 0 이하·N/A, 종가·기준일·출처 없음 또는 비정상",
  "basisNote": "최근 사업연도 재무제표와 표시된 기준일의 보통주 종가 사용",
  "priceAsOf": "2026-11-16"
}
```

모든 지표에 공식·사용 계정·설명·N/A 조건·기준 안내를 제공합니다.
기준 연도와 연결/별도는 data에, 실제 공시 접수번호와 기간은 meta.financialPeriods에 있습니다.
PER·PBR의 priceAsOf는 meta.market.closePriceAsOf와 같습니다. 다른 지표의 priceAsOf는 null입니다.
usedAccounts는 표준 재무 계정 목록이며 시세·주식수는 각각 meta.market과 data.shares에서 확인합니다.
증가율의 전기 계정은 meta에 표시된 전년 financials에서 확인할 수 있습니다.

N/A는 value=null과 status·reasonCode·reason으로 전달합니다. 표시값이 0이라고 결측으로 처리하지 않습니다.
사유 예: PREFERRED_SHARES, UNKNOWN_PREFERRED_SHARES, BASIS_MISMATCH, MISSING_PREVIOUS_YEAR,
NON_POSITIVE_EPS, MISSING_PRICE, ACCOUNT_NOT_AVAILABLE, INVALID_SHARES.
연간 자료가 전혀 없으면 200, data.status=NOT_COLLECTED, fiscalYear=null, metrics=[]입니다.
한 개 지표가 N/A인 경우에는 나머지 지표와 함께 200으로 응답합니다.

실제 패키지 실행 결과: [Phase 3 응답 증거](phase-3-responses.json).

## 연도별 이익률 API

`GET /companies/990001/metrics/profitability?years=5`는 `data.company`, `status`, `reason`, `years`를 반환합니다.
각 `years`에는 `fiscalYear`, `basis`, `metrics`가 있고, metrics는 OPERATING_MARGIN·NET_MARGIN 두 개입니다.
각 지표의 필드·BigDecimal 문자열·N/A 정책은 최신 metrics API와 같습니다. 2025년 가상 정상 사례는 각각 `10.000000`, `6.666667`입니다.

기존 계산 엔진을 재사용하며 과거 시세나 과거 PER·PBR을 만들지 않습니다. 확보된 연도만 최신순으로 반환합니다.
`meta.financialPeriods`의 같은 연도·기준 접수번호와 기간을 함께 확인해야 합니다. 차트에서 연결·별도를 섞거나 누락값을 0으로 채우면 안 됩니다.
REAL 수집·캐시·오류 처리는 financials와 동일하며 금융업은 422, years 범위 위반은 400입니다. DB 변경은 없습니다.

## 오류

| HTTP | error.code | 상황 |
|---|---|---|
| 400 | INVALID_REQUEST | 검색어 누락·공백, 잘못된 코드, 잘못된 years/limit |
| 403 | LOCAL_REQUEST_REQUIRED | 외부 Host·Origin·cross-site API 요청. 로컬 화면·CLI로만 요청 |
| 404 | COMPANY_NOT_FOUND | 모드 내에 해당 기업이 없음 |
| 404 | RESOURCE_NOT_FOUND | 없는 API 경로 |
| 405 | METHOD_NOT_ALLOWED | 지원하지 않는 HTTP 메서드 |
| 422 | UNSUPPORTED_INDUSTRY | 금융업 재무·지표 조회 |
| 500 | INTERNAL_ERROR | 예상하지 못한 내부 오류. 내부 예외 내용은 응답하지 않음 |

에러 응답 meta에도 mode가 포함됩니다. 실패 시 수집 시각이나 출처를 임의로 생성하지 않습니다.
REAL의 외부 오류는 502(응답·연결·정정 불일치), 503(키·인증·호출 제한), 504(시간 초과)로 구분합니다.
공급원 원본 오류 문구·인증키·전체 요청 URL을 공개 오류에 넣지 않습니다.

## REAL 수집과 갱신

0.6.0의 로컬 API는 loopback Host와 로컬 http/https Origin만 허용합니다. Origin 없는 로컬 PowerShell 요청은 허용합니다. `Origin: null`, 외부 Origin/Host, `Sec-Fetch-Site: cross-site`는 컨트롤러 실행 전에 거부합니다. CORS 허용은 확대하지 않으며 응답에 `X-Content-Type-Options: nosniff`를 추가합니다. 사용자 인증 기능은 아닙니다.

REAL 프로필에서 회사 검색·조회에 필요한 자료를 먼저 수집해 DB에 저장합니다.
처음 고유번호 ZIP과 필요한 기업 개황을 조회합니다. 재무 조회는 최신 공시 사업연도부터 최대 5개년을 수집합니다.
고유번호 목록은 하루 동안 메모리에서 재사용하고, 금융 자료는 DB를 재사용합니다.
CFS가 자료 없음(013)일 때만 OFS로 대체합니다. 인증·호출 제한을 연결 자료 없음으로 간주하지 않습니다.
현재 연도의 frmtrm_amount 등은 원본에만 남기고 과거 연도 값으로 채우지 않습니다.

financials의 각 연도에는 normalizationWarnings가 추가됩니다. Mock은 빈 배열입니다.
UNMAPPED_ACCOUNT, AMBIGUOUS_ACCOUNT, EPS_COMMON_SCOPE_UNCONFIRMED 등으로 확정하지 못한 항목을 알립니다.
원본 응답은 source API에서 확인합니다. 해당 연도·기준의 원본이 없으면 404 SOURCE_NOT_FOUND입니다.
원본에는 재무·기간·주식수·공시 식별정보를 보존하며 요청 키·헤더·URL은 저장하지 않습니다.

POST sync는 재무·시세를 독립적으로 갱신합니다. 실패해도 기존 정상 자료를 삭제하지 않습니다.
응답은 200과 CollectionResult이며 각 outcome을 검사해야 합니다.

```json
{
  "stockCode": "조회한 종목코드",
  "outcomes": [
    {"type":"FINANCIALS","status":"SUCCESS","itemsSaved":5,"errorCode":null,"message":"연간 자료를 저장했습니다."},
    {"type":"MARKET","status":"FAILED","itemsSaved":0,"errorCode":"FSC_KEY_MISSING","message":"FSC_API_KEY를 설정해야 종가를 조회할 수 있습니다."}
  ]
}
```

위 JSON은 구조 예시이며 실제 기업 실행 결과가 아닙니다.
일반 조회는 저장 자료를 재사용하므로 최신 거래일·새 정정본을 자동 보장하지 않습니다. 필요할 때 sync를 호출합니다.
시세 실패는 재무 기반 지표를 차단하지 않습니다. 저장된 정상 종가가 있으면 기준일·수집일·stale과 함께 사용할 수 있습니다.
최초 재무 수집 실패는 HTTP 오류이며, 최근 5분 내 일반 조회는 같은 실패 이력을 재사용합니다.
키 없는 REAL 조회는 503 DART_KEY_MISSING입니다. Mock 수치로 대체하지 않습니다.

실제 인증 호출·5개년·시세·재수집과 실응답 테스트를 확인했습니다. [Phase 4 검증 상태](phase-4-verification.md)를 확인하세요.

## 수동 확인

1. 990001을 검색하고 기업 기본정보와 5개년 재무를 조회합니다. meta.mode가 MOCK인지 확인합니다.
2. 2025년이 CFS이고 같은 연도가 중복되지 않는지 확인합니다.
3. 990002의 BASIC_EPS가 음수이며 AVAILABLE인지 확인합니다.
4. 990003의 hasPreferredShares=true와 연도별 preferredShares를 확인합니다. metrics의 BPS/PBR은 null + PREFERRED_SHARES여야 합니다.
5. 990004는 2개년, CFS/OFS 변경, EPS null, 3월 결산, 종가 없음이 각각 구분되는지 확인합니다.
6. 990005 재무 요청은 422, 000000은 404, years=6은 400인지 확인합니다.
7. 990001/metrics의 PBR=3.040000, ROE=11.000000, 990002/metrics의 PER=N/A를 확인합니다.
8. 990004/metrics의 증가율은 BASIS_MISMATCH이며, 종가가 없어도 ROA=4.000000은 제공되는지 확인합니다.

실제 DART 공시 화면의 사용자 직접 대조는 미확인입니다. [실제 수치 대조표](live-verification-table.md)와 [수동 검증 방법](manual-verification.md)을 사용합니다. 자동 테스트·수집 성공과 별도로 판정합니다.

## PowerShell 호출 예

권장 REAL 서버를 켠 상태에서 별도 PowerShell 창으로 조회합니다. 조회 요청은 저장 자료를 재사용하며, 마지막 sync는 명시적 갱신을 원할 때 실행합니다.

```powershell
$apiBase = 'http://127.0.0.1:18095/api/v1/companies'
Invoke-RestMethod ($apiBase + '?query=000270')
Invoke-RestMethod ($apiBase + '/000270')
Invoke-RestMethod ($apiBase + '/000270/financials?years=5')
Invoke-RestMethod ($apiBase + '/000270/metrics')
Invoke-RestMethod ($apiBase + '/000270/metrics/profitability?years=5')
```

```powershell
$result = Invoke-RestMethod -Method Post ($apiBase + '/000270/sync') -TimeoutSec 240
$result.data.outcomes | Format-Table type, status, itemsSaved, errorCode, message -AutoSize
```

첫 실제 수집은 여러 외부 요청을 포함해 시간이 걸릴 수 있습니다. 필요하면 조회에도 `-TimeoutSec 240`을 지정합니다. 고정 종목코드는 재현 예시이며 사용자는 기업명·종목코드로 선택합니다.
