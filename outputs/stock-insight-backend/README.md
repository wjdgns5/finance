# Stock Insight 백엔드 — 0.6.0

Java21·Spring Boot·MySQL 기반의 기업 검색·연간 재무·지표 API입니다. 실제 공급원은 DART 재무와 금융위원회 시세입니다. Mock은 외부 키 없이 같은 저장·조회 경로를 재현하는 가상 데이터입니다.

**프로젝트 전체 시작점은 [프로젝트 README](../README.md)**입니다. 화면까지 한 번에 켜고 끄는 방법은 [PowerShell 안내](../stock-insight-frontend/docs/local-run-guide.md)에 있습니다. Phase7에서 문서를 현재 구현에 맞춰 정리했으며 앱 버전·금융 기준·DB 스키마는 변경하지 않았습니다.

실제 인증·수집·계산·브라우저 검증을 수행했지만 사용자의3종목 공시 원문 대조는 미확인입니다. 이전 Stock Insight 소스는 제공되지 않아 별도 기반으로 구성했으며 기존 서비스의 회귀·성능 비교 결과는 없습니다.

## 환경·빌드

| 항목 | 고정·검증 환경 |
|---|---|
| Java |21, Amazon Corretto21.0.12 |
| Spring Boot |3.5.16 |
| Gradle |Wrapper8.10.2, 배포ZIP SHA-256 검증 |
| DB |native MySQL8.0.46 검증, Compose 설정8.4.13 |
| DB 변경 |Flyway V1/V2, JPA ddl-auto=validate |
| 주요 테스트 |JUnit Jupiter5.12.2·Mockito5.17.0 |
| 주요 DB 의존성 |Flyway11.7.2·MySQL Connector/J9.7.0 |
| 실행 JAR |build/libs/stock-insight-backend-0.6.0.jar |

```powershell
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk21.0.12_8'
Set-Location 'C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend'
.\gradlew.bat bootJar
```

다른 설치 경로에서는 JAVA_HOME과 프로젝트 경로를 바꿉니다. 최초 의존성 다운로드에는 네트워크가 필요할 수 있습니다. 금융 계산은 BigDecimal, DB 금액은 DECIMAL, JSON 금액은 문자열입니다.

## 실행 선택

평소 사용은 프론트엔드의 단일 실행 명령이 DB·백엔드·화면을 함께 켭니다. Mock/Real/QA의 백엔드 포트는18094/18095/18096입니다. 필요한 모드만 실행하면 됩니다.

백엔드만 따로 개발할 때는 다음 대안을 사용합니다. 아래 기본 주소는8080이며 위 모드별 포트와 다릅니다.

### Docker Compose

Docker Desktop 엔진을 실행한 뒤 백엔드 폴더에서 실행합니다.

```powershell
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
}
.\scripts\run-local.ps1
```

MySQL 준비 후 bootRun을 실행합니다. 기본 백엔드8080·DB13306, loopback 바인딩입니다. 종료는 Ctrl+C, DB 보존 중지는 `docker compose stop`입니다. 현재 PC의 실제 검증은 native MySQL로 했으며 Docker 엔진 실행 경로는 미검증입니다.

### 별도로 준비한 개발 MySQL

현재 PC에서 IntelliJ Run·Debug로 실행하려면 **[IntelliJ·VS Code 안내](../docs/ide-development-guide.md)**를 사용합니다. `scripts/start-ide-db.ps1`은 설치된 MySQL만 전용 포트13397에서 시작하며, 백엔드·프론트는 IDE에서 각각 실행합니다.

앱 전용 빈 DB와 사용자 권한을 준비한 뒤 실행합니다.

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/stock_insight_mock?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
$env:DB_USERNAME = 'stock_insight'
$env:DB_PASSWORD = 'your_local_password'
.\scripts\run-local.ps1 -SkipDocker
```

REAL 설정·프로필·API 키는 [키 설정과 실행](docs/api-keys-and-real-run.md)을 따릅니다. 같은 앱 DB를 Mock·Real용으로 임의 변경하지 않고 별도 DB를 사용합니다.

## 환경변수

Spring Boot가 `.env` 파일을 자동으로 읽지 않습니다. 제공 스크립트가 허용한 이름을 프로세스 환경변수로 넘깁니다. 예시는 [Mock 환경](.env.example)·[Real 환경](.env.real.example)에 있으며 실제 설정은 Git 제외 대상입니다.

| 이름 | 사용처·의미 |
|---|---|
| SPRING_PROFILES_ACTIVE |mock 또는 real |
| APP_DATA_MODE |MOCK 또는 REAL. 프로필과 함께 맞춤 |
| DART_API_KEY |DART 인증키, Real 수집에 필요 |
| FSC_API_KEY |승인된 금융위원회 서비스의 Decoding 키. 미설정 시 시세 관련 N/A |
| DB_URL·DB_USERNAME·DB_PASSWORD |Spring datasource 설정. 백엔드 단독 실행·기존 개발DB 경로 |
| MYSQL_DATABASE·MYSQL_USER·MYSQL_PASSWORD·MYSQL_ROOT_PASSWORD·MYSQL_PORT |Compose 및 실행 스크립트의 DB 구성 |
| SERVER_PORT |백엔드 포트. 모드 래퍼는 전용 포트를 명시적으로 지정 |
| MYSQL_IT_URL·MYSQL_IT_UPGRADE_URL·MYSQL_IT_USER·MYSQL_IT_PASSWORD |테스트 전용. 제공 verify-mysql 스크립트가 임시 설정 |

화면·DB를 함께 켜는 모드 래퍼는 격리용 포트·DB 경로를 명시합니다. `.env.real`의 기본 포트보다 래퍼 인자가 우선합니다. 실제 키·키가 들어간 요청 URL을 문서·로그에 출력하지 않습니다.

## API 확인

**[Swagger UI·OpenAPI 안내](docs/swagger-guide.md)**를 추가했습니다. 백엔드 실행 후 [8080 Swagger](http://127.0.0.1:8080/swagger-ui.html)에서 Try it out → Execute로 호출합니다. Real 일괄 실행은 [18095 Swagger](http://127.0.0.1:18095/swagger-ui.html), JSON 명세는 해당 백엔드의 `/v3/api-docs`입니다. 변경을 처음 반영할 때 Gradle 동기화·백엔드 재시작이 필요하며 JAR 실행은 bootJar로 빌드합니다.

[API 명세](docs/api.md)에7개 요청·파라미터·응답·오류를 정리했습니다. 권장 Mock 서버를 켠 뒤 다른 PowerShell 창에서 확인합니다.

```powershell
$apiBase = 'http://127.0.0.1:18094/api/v1/companies'
Invoke-RestMethod ($apiBase + '?query=990001')
Invoke-RestMethod ($apiBase + '/990001/financials?years=5')
$result = Invoke-RestMethod ($apiBase + '/990001/metrics')
$result.data.metrics | Format-Table code, value, status, reasonCode -AutoSize
```

가상 사례는990001 정상,990002 적자,990003 우선주,990004 자료 부족·3월 결산·기준 변경,990005 금융업 미지원입니다. 실제 값·접수번호가 아닙니다. REAL에는 DART 자료와 금융위원회 종가를 사용하며 `POST sync`는 재무·시세 outcome별 성공 여부를 확인합니다.

## 전체 테스트

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend\scripts\verify-mysql.ps1"
```

새 전용 MySQL 인스턴스·DB2개에서 실제로 테스트를 재실행하고222개·실패0·오류0·건너뜀0을 확인합니다. Swagger 추가 전 Phase6/7 기록은217개이며 문서 계약·보안 검사5개를 추가했습니다. [Swagger 검증 기록](docs/swagger-verification.json)에 결과를 남깁니다. 완료 후 자신이 시작한 DB만 종료합니다. Windows PowerShell5.1에서 직접 실행했습니다. 일반 `gradlew test`는 DB 환경이 없으면 통합 테스트를 건너뜁니다.

실제 DART 샘플68개 테스트와 MySQL·Flyway 보존·REAL upstream 계약·보안 요청 경계가 포함됩니다. [재현과 검증 상태](../docs/verification.md)를 기준으로 자동·실제 공급원·브라우저·사용자 수동 검증을 구분합니다.

## 구조·정책

- [소프트웨어 설계 명세서(SDD)](../docs/SDD.md): 요구사항·모듈·데이터·API·운영·검증 추적.
- [아키텍처](docs/architecture.md): 수집·정규화·DTO·트랜잭션·캐시·프론트 흐름.
- [DB 명세·ERD](docs/database.md):5개 테이블·PK/FK·Unique·인덱스·V1/V2·갱신 방식.
- [금융 정책5.3·5.6](docs/financial-policy.md):공식·사용 계정·N/A·단위·정밀도·제한의 기준 문서.
- [설계 결정](../docs/design-decisions.md): 선택 이유·비용·대안.
- [포트폴리오 설명](../docs/portfolio.md): 코드·시연·면접 근거.

연간만 지원하고 금융업은 제외합니다. ROE·ROA 기말 기준, 우선주 BPS·PBR N/A, 공시 보통주 EPS 미확정 시 EPS·PER N/A입니다. 과거 EPS의 주식 분할·무상증자 단절과 재작성 전기값을 자동 보정하지 않습니다. 저장 시세는 실시간·최신 거래일 보장이 없습니다.

스키마는 Flyway만 바꾸며 적용한 V1/V2를 수정하지 않습니다. 정정·수집 실패 시 기존 정상 스냅샷을 보존하고 stale를 표시합니다. 로컬 요청 경계는 외부 Origin·Host·cross-site 요청을403으로 차단하지만 사용자 인증은 아닙니다. 공개 배포는 포함하지 않습니다.

## 확인할 남은 항목

[3종목 실제 공시 대조표](docs/live-verification-table.md)의 사용자 확인이 남아 있습니다. 원본 공시의 단위·기본/희석·보통주 범위·주석을 읽는 확인을 자동 검사로 대체하지 않습니다. 자세한 미검증 범위와 역사적 실행 기록은 [Phase6 결과](../stock-insight-frontend/docs/phase-6-verification.md), [Phase7 문서 보고](../docs/phase-7-documentation.md)에 연결합니다.
