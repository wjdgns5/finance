# Swagger UI·OpenAPI 사용 안내

Stock Insight 0.6.0 백엔드에 Swagger UI와 OpenAPI 3.0 명세를 추가했습니다. Controller·DTO·검증 제약에서 명세를 생성하며 한국어 설명·모드별 입력 예시·공통 오류를 제공합니다.

## 1. 준비와 접속

Swagger도 실행 중인 백엔드에서 제공합니다. 처음 변경을 반영할 때 IntelliJ에서 **Gradle 동기화 후 백엔드를 재시작**하세요. 기존 PowerShell JAR 실행을 쓰면 백엔드 폴더에서 `gradlew.bat bootJar`로 빌드하고 해당 서버를 재시작합니다. 이후 일반적인 재실행 때마다 다시 빌드할 필요는 없습니다.

이 작업에서 사용하는 라이브러리는 `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.10.0`입니다. Spring Boot 3.5 호환 조합과 기본 경로는 [springdoc 공식 문서](https://springdoc.org/v2/)를 기준으로 선택했습니다.

| 실행 방법 | Swagger UI | OpenAPI JSON |
|---|---|---|
| IntelliJ 개발·8080 |[Swagger](http://127.0.0.1:8080/swagger-ui.html) |[JSON](http://127.0.0.1:8080/v3/api-docs) |
| Mock 일괄·18094 |[Swagger](http://127.0.0.1:18094/swagger-ui.html) |[JSON](http://127.0.0.1:18094/v3/api-docs) |
| Real 일괄·18095 |[Swagger](http://127.0.0.1:18095/swagger-ui.html) |[JSON](http://127.0.0.1:18095/v3/api-docs) |
| QA 백엔드·18096 |[Swagger](http://127.0.0.1:18096/swagger-ui.html) |[JSON](http://127.0.0.1:18096/v3/api-docs) |

YAML은 같은 백엔드의 `/v3/api-docs.yaml`입니다. `/swagger-ui.html`은 실제 UI 경로로 이동합니다. 화면의 `/swagger-ui/index.html`을 직접 열어도 됩니다.

Swagger는 **백엔드 포트**로 엽니다. 프론트 화면5173·5194·5195·5206에서 같은 경로를 열지 않습니다. 백엔드 포트를 바꿨다면 주소도 바꿉니다. 명세의 서버 주소는 `/`이므로 해당 Swagger 페이지의 백엔드로 실행됩니다.

서버 켜는 순서는 [IntelliJ·VS Code 안내](../../docs/ide-development-guide.md), 일괄 실행은 [PowerShell 안내](../../stock-insight-frontend/docs/local-run-guide.md)를 따릅니다. Swagger만 별도 설치할 필요는 없습니다. 실행에는 기존과 같이 MySQL이 필요합니다.

## 2. 조회 실행

1. 백엔드를 실행한 뒤 위 Swagger 주소를 엽니다.
2. 상단 설명의 **현재 데이터 모드**를 확인합니다.
3. `기업·재무` → `GET /api/v1/companies`를 펼칩니다.
4. **Try it out**을 누르고 query에 Mock은 `한빛`, Real은 `기아`를 입력합니다.
5. **Execute**를 누르고 `200`과 `Response body`의 기업 목록을 확인합니다.
6. 반환된 stockCode로 기업 상세·financials·metrics·profitability를 실행합니다. 종목코드의 선행 0을 유지합니다.

Mock 예시는990001, Real 예시는000270입니다. limit는1~50, years는1~5입니다. 최초 Real 조회에는 외부 수집·DB 저장이 포함되어 시간이 걸릴 수 있습니다.

`Try it out`은 처음부터 실행 상태로 펼치지 않습니다. 사용자가 버튼과 Execute를 눌러 요청합니다. Swagger 화면을 읽거나 명세를 내려받는 것만으로 기업 수집을 시작하지 않습니다.

## 3. 모드별 API

| 모드 | 표시되는 API |
|---|---|
| MOCK |검색·상세·연간 재무·최신 지표·수익성 이력, 총5개 |
| REAL |위5개 + POST sync·GET source, 총7개 |

REAL 전용 Controller는 real 프로필에서만 등록되므로 Mock Swagger에 관리 API가 표시되지 않는 것이 정상입니다. 별도 로그인이나 Authorize 설정은 없으며 기존 로컬 요청 정책을 적용합니다. DART·FSC 키는 서버의 `.env.real` 또는 IDE 환경변수에서 읽습니다. **Swagger 입력칸에 키를 넣지 않습니다.**

### POST sync

`실제자료 관리`의 `POST /api/v1/companies/{stockCode}/sync`는 외부 API를 다시 호출하고 DB를 갱신합니다. 명시적 갱신이 필요할 때 실행합니다. `200`이어도 `data.outcomes`의 FINANCIALS·MARKET 각각에서 SUCCESS/FAILED·errorCode·message를 확인합니다. 시세 실패와 재무 실패는 구분합니다.

### GET source

`GET /api/v1/companies/{stockCode}/financials/{year}/source`는 이미 저장한 원본을 조회합니다. financials 응답에 있는 연도·CFS/OFS를 지정합니다. 해당 원본이 없으면404 SOURCE_NOT_FOUND입니다. 이 요청은 새 수집을 시작하지 않습니다.

## 4. 응답을 읽는 방법

| 항목 | 의미 |
|---|---|
| data·meta·error |공통 바깥 구조. 성공 error=null, 실패 data=null |
| 금액·지표·주식수 |따옴표가 있는 JSON 문자열. 큰 수의 정밀도 보존 |
| value=null |N/A. status·reasonCode·reason과 함께 확인 |
| value가0·음수 |확인된 실제 수치일 수 있으므로 결측과 구분 |
| financialPeriods |사업연도·연결/별도·접수번호·실제 기간·출처 |
| market |종가·시가총액 기준일·출처. 시세 없으면null |
| stale |최신 수집 이력에 실패가 있음. false가 최신 자료를 보장하지 않음 |

Schemas에서 DTO 필드·enum·nullable·문자열 타입을 확인할 수 있습니다. `Example Value`는 스키마 설명용 예시이며 실제 응답은 **Execute 후 Response body**로 확인합니다. 공식·N/A 기준은 [금융 정책](financial-policy.md), 전체 계약은 [API 명세](api.md)를 참조하세요.

오류 응답에는400 입력 오류·403 로컬 요청 위반·404 미존재·422 금융업 미지원·502/503/504 외부 오류 등의 공통 구조와 예시를 제공합니다. 내부 예외·키·JDBC 주소는 공개하지 않습니다.

## 5. 명세 저장과 유지보수

실행 중인 서버의 `/v3/api-docs`·`/v3/api-docs.yaml`을 저장해 도구에 가져올 수 있습니다. 저장한 파일은 생성 시점의 모드·버전 스냅샷입니다. Controller가 바뀌면 서버를 재시작해 다시 생성·저장합니다.

현재 구현에서 생성한 스냅샷은 [Mock JSON](openapi-mock.json)·[Mock YAML](openapi-mock.yaml), [Real JSON](openapi-real.json)·[Real YAML](openapi-real.yaml)입니다. 다른 도구에 가져올 때는 기본 URL을 실행 중인 백엔드 주소로 지정합니다. 파일의 상대 서버 주소 `/`만으로는 외부 도구가 PC의 서버 위치를 알 수 없습니다.

| 변경 내용 | 수정할 곳 |
|---|---|
| 경로·파라미터·기능 설명 |CompanyController·MetricController·RealDataController의 @Operation·@Parameter |
| DTO 필드·수치·nullable |ApiModels·MetricModels의 @Schema 및 실제 JSON 직렬화 |
| 공통 정보·오류·모드별 예시 |api/OpenApiConfig.java |
| 문서 경로·UI 옵션 |application.yml의 springdoc |
| 로컬 접근 경계 |LocalApiPolicy와 보안 회귀 테스트 |

외부 온라인 validator는 사용하지 않으며 Swagger UI 자원은 백엔드에 포함됩니다. 문서·UI 경로에도 기존 Host·Origin·cross-site 정책을 적용합니다. 설정의 의미는 [springdoc 옵션](https://springdoc.org/v2/properties.html)을 참조하세요.

## 6. 문제 해결

| 현상 | 확인 |
|---|---|
| 연결할 수 없음 |MySQL과 백엔드 시작 완료 여부·백엔드 포트 |
| Swagger404 |새 Gradle 의존성 동기화·JAR 빌드·백엔드 재시작 여부 |
| Failed to load API definition |같은 백엔드의 /v3/api-docs 응답과 백엔드 로그 |
|403 LOCAL_REQUEST_REQUIRED |127.0.0.1 또는 localhost로 직접 열었는지 확인 |
| 관리 API가 없음 |상단 모드와 real 프로필 여부 |
| Real 요청503 |서버 환경변수의 키·프로필·서비스 승인 상태 확인 |
| sync200인데 자료가 없음 |outcomes별 FAILED·오류 사유 확인 |

수동 공시 대조·미검증 범위는 기존 [검증 안내](../../docs/verification.md)를 유지합니다. Swagger 추가가 사용자 공시 대조 완료를 의미하지는 않습니다.

## 7. 이번 추가의 검증 결과

기존217개에 Swagger 검사5개를 더해 **백엔드222개·실패0·오류0·건너뜀0**을 격리 MySQL에서 확인했습니다. Mock5개·Real7개 경로, 문자열 수치·nullable·enum, 문서 요청의 무수집·키 비노출, UI 자원·설정, 외부 Origin 차단을 포함합니다.

빌드한 JAR에서도 두 모드의 UI 리다이렉트·HTML·JS/CSS·JSON/YAML·접근 정책을 HTTP로 확인했습니다. 자동 브라우저는 실행 환경의 localhost 연결 제한으로 접속에 실패해 Swagger 화면에서 버튼을 누르는 검증은 완료하지 못했습니다. [실행 기록](swagger-verification.json)·[JUnit XML](test-results/swagger/)에 확인 범위를 구분했습니다.
