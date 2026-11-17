# API 키 발급과 REAL 실행

현재 상태: **실제 인증 호출·수집·정규화·계산 테스트 완료, 사용자 공시 화면 직접 대조는 미확인**입니다.
2026-11-17 .env.real의 키 위치와 FSC 인코딩을 수정해 정상 연결을 확인했습니다. 아래는 재설정·재실행 안내입니다.
키가 없어도 기존 MOCK 화면은 계속 사용할 수 있습니다.

## 1. DART 키 준비

1. [OpenDART 인증키 신청](https://opendart.fss.or.kr/uss/umt/EgovMberInsertView.do)을 엽니다.
2. 신청 화면에 본인의 정보와 실제 사용 환경·용도를 입력합니다.
3. 발급된 키와 접근 IP 설정을 확인합니다. 발급·승인에 걸리는 시간은 보장하지 않습니다.
4. 키는 채팅에 붙이지 말고 아래 로컬 설정 파일에 입력합니다.

DART만 준비되면 기업·재무자료와 주가가 필요 없는 지표를 검증할 수 있습니다.

## 2. 시세 키 준비

1. 공공데이터포털의 [금융위원회_주식시세정보](https://www.data.go.kr/data/15094808/openapi.do)에서 활용신청합니다.
2. 해당 서비스의 활용 승인 상태와 인증키를 확인합니다.
3. **Decoding 키** 원문을 FSC_API_KEY에 입력합니다. 프로그램이 요청할 때 한 번 URL 인코딩합니다.

시세는 실시간이 아닙니다. 조회한 종가 기준일과 출처를 응답 meta에서 확인합니다.
현재 구현은 V2 경로를 사용합니다. 갱신 주기·제3자 재배포·상업적 이용 조건은 실제 이용 시 같은 공식 안내에서 확인합니다.
이번 구현과 검증은 로컬 개발 범위이며 공개 배포는 포함하지 않습니다.

## 3. 로컬 파일 설정

백엔드 전체 경로:
`C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend`.

PowerShell을 새로 열고 다음을 실행합니다. 기존 파일이 있으면 복사하지 않고 내용을 확인합니다.

```powershell
Set-Location 'C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend'
if (-not (Test-Path -LiteralPath '.env.real')) {
    Copy-Item .env.real.example .env.real
}
notepad .env.real
```

입력할 부분:

```dotenv
DART_API_KEY=발급받은_DART_키
FSC_API_KEY=활용_승인받은_Decoding_키
```

실제 키로 예시 문구를 교체합니다. FSC 키가 없으면 `FSC_API_KEY=`처럼 비워 둡니다.
키 값에 따옴표나 공백을 붙이지 않습니다. .env와 .env.real은 Git 제외 대상입니다.
이 설정 파일은 Spring Boot가 직접 읽는 파일이 아니라 제공한 실행 스크립트가 환경변수로 읽습니다.

## 4. 실행

현재 PC에서는 설치된 MySQL·Java·Node를 사용하는 아래 PowerShell 명령으로 DB·백엔드·화면을 함께 시작할 수 있습니다.
저장된 .env.real을 읽으며 Docker가 필요하지 않습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-phase5-local.ps1" -Mode Real
```

READY (REAL) 후 [화면](http://127.0.0.1:5195)에서 기아를 검색하세요. 사용 중에는 터미널을 열어 두고 Enter로 종료합니다.
권장 API 주소는 http://127.0.0.1:18095, 데이터 저장 경로는 work/phase5-browser-real/mysql-data입니다. 실행 파일 이름과 관계없이 현재0.6.0 JAR을 사용합니다. 재시작은 [PowerShell 안내](../../stock-insight-frontend/docs/local-run-guide.md)를 따릅니다.
Windows PowerShell 5.1에서 최초 DB 생성·서버·프록시 기동을 확인했습니다.
Docker 방식 또는 이미 준비한 다른 MySQL을 쓰려면 아래 안내를 사용합니다.

JDK 21과 실행 중인 Docker Desktop 엔진이 준비되어 있을 때:

```powershell
.\scripts\run-local.ps1 -EnvironmentFile .env.real
```

REAL 전용 Compose 프로젝트와 볼륨을 사용합니다. 예시 포트는 DB 13307, 백엔드 8081입니다.
기존 Mock DB·볼륨을 바꾸지 않습니다. Compose의 새 볼륨에서는 REAL DB를 최초 생성합니다.
기존 볼륨의 비밀번호는 .env.real을 수정해도 자동 변경되지 않습니다.

Docker 엔진을 사용할 수 없으면 설치된 MySQL에서 **별도의 빈 개발 DB와 전용 사용자**를 먼저 준비하고,
.env.real에 DB_URL·DB_USERNAME·DB_PASSWORD를 지정한 뒤 실행합니다.

```powershell
.\scripts\run-local.ps1 -EnvironmentFile .env.real -SkipDocker
```

이번 자동 검증은 설치된 MySQL 8.0.46의 전용 임시 인스턴스로 수행했습니다.
Docker 엔진 경로는 직접 확인하지 않았습니다. 실제 API 검증은 별도 native MySQL에서 수행했고, 사용자가 켠 native REAL 서버의 실제 브라우저 흐름도 확인했습니다.
기존 브라우저 확인용 Mock DB와 같은 DB를 REAL DB로 지정하지 않습니다.

처음 기업을 조회하면 고유번호 ZIP → 기업 개황 → 연간 재무·기간·주식수 → 시세 순으로 수집해 저장합니다.
5개년은 최신 공시된 사업연도를 끝으로 요청하고, 확인된 보고서가 없으면 해당 연도를 생략합니다.
원본 계정 금액·접수번호·실제 보고 기간·출처를 보존합니다.

```powershell
$stockCode = Read-Host '조회할 실제 기업의 보통주 종목코드 6자리'
$base = 'http://127.0.0.1:18095/api/v1/companies/' + $stockCode
Invoke-RestMethod ($base + '/financials')
Invoke-RestMethod ($base + '/metrics')
Invoke-RestMethod -Method Post ($base + '/sync')
```

sync는 명시적 갱신입니다. 재무·시세 결과가 outcomes에 각각 반환되므로 HTTP 200만으로 둘 다 성공했다고 판단하지 않습니다.
저장 자료는 일반 조회에서 다시 사용합니다. 최신 공시·시세 반영이 필요하면 sync를 호출합니다.

## 5. 확인할 오류

| 코드 | 점검 |
|---|---|
| DART_KEY_MISSING | DART_API_KEY가 실제 서버 프로세스에 설정되었는지 확인 |
| DART_AUTH_ERROR | 발급 키·사용 가능 상태·접근 IP 확인 |
| DART_RATE_LIMIT | 호출 한도 확인. 일반 조회는 저장값과 최근 실패 이력을 재사용 |
| DART_PERIOD_UNCONFIRMED | 공시된 당기 기간을 확인할 수 없음. 원본 응답을 분석해야 함 |
| DART_REVISION_PENDING | 최신 공시와 재무 응답 접수번호 불일치. 기존 자료 유지 |
| FSC_KEY_MISSING / FSC_SERVICE_ERROR | 시세 키와 해당 서비스 활용 승인 확인 |
| PROVIDER_TIMEOUT | 외부 서버 지연. 마지막 정상 자료와 stale 확인 |

오류를 공유할 때는 error.code와 한국어 message만 알려주세요. 키가 포함된 전체 요청 URL은 공유하지 않습니다.

## 6. 실제 검증 마무리

일반 제조업·적자·우선주 발행 기업 각각 한 곳을 골라 [3종목 대조표](manual-verification.md)를 작성합니다.
실제 호출이 성공한 후에는 아래 스크립트로 세 기업의 원본 응답을 로컬에 보관할 수 있습니다.

```powershell
$codes = (Read-Host '세 기업의 보통주 종목코드를 쉼표로 구분').Split(',').Trim()
.\scripts\export-live-samples.ps1 -StockCodes $codes
```

실제 응답 저장 후 정규화·계산 테스트 68개를 추가해 통과했습니다. 새 기업을 추가할 때도 독립 기대값을 준비합니다.
파일 저장만으로 사용자 수치 검증이 끝난 것은 아닙니다. 단계별 구현·문서화와 별개로3종목 공시 화면 사용자 대조는 미확인 상태를 유지합니다.
