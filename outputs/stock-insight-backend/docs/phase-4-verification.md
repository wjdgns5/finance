# Phase 4 — 구현 및 검증 상태

2026-11-17, Asia/Seoul. 현재 버전 0.4.1.
**실제 인증 호출·수집·저장·갱신·실응답 테스트 완료, 사용자 공시 화면 대조는 미확인**입니다.
.env.real의 두 키 입력 위치와 FSC 인코딩을 바로잡고 DART 000·FSC 00 정상 응답을 확인했습니다.
키 값은 응답·문서·테스트 샘플에 포함하지 않았습니다. 사용자는 수동 대조가 어렵다고 답해 미수행 상태로 남겼습니다.
Phase 5는 시작하지 않았습니다.

## 실제 검증 결과

- 기아(000270), 롯데케미칼(011170), 삼성전자(005930): 각각 2021~2025년 5개년 CFS와 실제 종가 수집·재수집 성공.
- 시세 출처 FSC, 종가 기준일 2026-11-16. 세 기업의 재무·시세 sync 결과 모두 SUCCESS, stale=false.
- 9월 결산 금비(008870): 2025년 실제 기간 2024-10-01~2025-09-30과 5개년 조회 확인. 결산일을 12월로 가정하지 않음.
- 롯데케미칼의 2025년 보고서 전기 매출과 2024년 보고서 당기 매출이 다른 실제 사례 확인. 승인된 각 연도 보고서 기준 유지.
- 삼성전자에서 동일 순이익 계정의 IS/CIS 반복을 확인해 정규화 보완. ID·이름·상세·금액이 같은 한 쌍만 IS 선택, 상충·동일 표 중복은 계속 N/A.
- 삼성전자의 일반 기본 EPS 행은 보통주 범위를 확정할 수 없어 EPS·PER N/A 유지. 우선주 발행으로 BPS·PBR N/A 확인.
- LG이노텍 조회에서는 공시·재무 접수번호 불일치로 DART_REVISION_PENDING을 반환. 정정본 확인 없이 갱신하지 않는 보호 동작 확인.

[실제 호출 요약](phase-4-live-summary.json), [원본과 대조용 수치](live-verification-table.md), [실응답 테스트 자료](../src/test/resources/dart-live/README.md).

## 구현한 흐름

REAL 프로필에서 검색·조회 전에 수집 서비스가 필요한 자료를 준비하고, 기존 읽기 전용 조회 서비스가 응답합니다.
외부 호출 중에는 DB 쓰기 트랜잭션을 잡지 않습니다. 수집이 끝난 재무 묶음을 별도 쓰기 서비스가 원자적으로 저장합니다.
실패 이력은 정상 자료를 보존한 채 기록합니다. 조회와 지표 계산은 기존 응답 구조와 DataStatus를 재사용합니다.

| 구현 | 위치 / 역할 |
|---|---|
| DART Client | provider/dart/DartClient.java — 고유번호 ZIP, 기업개황, 공시 목록·페이지, 전체 재무, 실제 기간, 주식수 |
| 외부 DTO | provider/dart/DartModels.java — 공급원 응답과 원본 JSON |
| 정규화 | provider/dart/DartNormalizer.java — 표준 ID·표 종류 확인, 당기값, 원본 보존, 미매핑·중복·단위 검사 |
| 실제 재무 Provider | provider/dart/DartFinancialDataProvider.java — 최신 공시 연도부터 5개년, CFS 우선·OFS 대체 |
| 시세 Provider | provider/fsc/FscMarketDataProvider.java — 금융위원회 V2, 30일 범위·페이지·정확한 종목코드·최신 기준일 |
| 통신 | provider/ExternalHttp.java — 연결 5초·전체 요청 15초 제한, 응답 크기 제한, 키를 포함하지 않는 오류 |
| 수집·갱신 | service/RealDataService.java — DB 재사용, 명시적 sync, 최근 실패 재호출 억제 |
| 저장 | service/RealDataWriter.java — 동일 보고서 갱신, 과거 접수번호로 되돌리지 않음, 시세 스냅샷 중복 방지 |
| 추가 API | api/RealDataController.java — sync와 원본 응답 조회 |

기준 폴더: `C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend\src\main\java\com\stockinsight`.

원본 재무·기간·주식수 응답과 공시 식별정보를 DB에 보존합니다.
미매핑 진단은 normalizationWarnings로 제공합니다. 응답 크기와 TEXT 저장 한도를 위해 최대 100개·각 180자로 제한하며
초과 시 WARNINGS_TRUNCATED_SEE_SOURCE를 표시합니다. 전체 원본은 source API에 남습니다.
추측한 이름 매핑, 공시 EPS 역산, 우선주 자본 근사 배분을 하지 않습니다.
보통주 범위를 확정할 수 없는 EPS는 미매핑으로 남깁니다.
삼성전자 2025년 주요 재무지표 API 4분류도 실제 호출했고 해당 응답에서 BPS 행은 발견하지 못했습니다.
이 관찰을 전체 기업·전체 API의 제공 불가로 일반화하지 않습니다. 승인된 조건부 BPS 계산 기준은 유지합니다.
[조회 범위와 결과](live-bps-availability.json).

## 저장·갱신과 영향

- V1 마이그레이션은 수정하지 않았습니다. V2는 financial_statement에 nullable raw_payload LONGTEXT·normalization_notes TEXT만 추가합니다.
- 기존 Mock 계정·금액·접수번호와 기존 API 필드는 보존합니다. FinancialYear에 normalizationWarnings 배열을 추가했습니다.
- READ API는 처음 필요한 자료를 수집한 뒤 DB를 재사용합니다. 최신 공시·시세의 재수집은 POST sync입니다.
- 고유번호 목록만 하루 동안 메모리에서 재사용합니다. 금융 수치 캐시는 DB이며 별도 캐시 라이브러리는 없습니다.
- 최신 정정 접수번호와 재무 API 접수번호가 맞지 않으면 갱신하지 않습니다. 실제 정정 구조를 확인한 결과는 아닙니다.
- 같은 연도에 CFS가 더 이상 제공되지 않고 동등 이상 접수번호의 OFS가 선택되면 오래된 CFS가 계속 선택되지 않도록 교체합니다.
- 이후 보고서의 전기·전전기 값으로 과거 연도를 덮어쓰지 않습니다.
- 수집 실패 후 저장된 정상 자료는 유지하고 meta.stale=true를 반환합니다. 시세 실패가 재무 조회를 차단하지 않습니다.
- 최초 재무 수집이 실패하면 502/503/504 오류를 반환합니다. 최근 실패 후 5분 이내 일반 조회는 같은 실패를 재사용합니다.
- REAL에서 Mock 기업이나 수치를 대체 제공하지 않습니다. 키 없이 시작한 REAL JAR은 조회 시 503 DART_KEY_MISSING입니다.

## 자동 테스트

**211개 통과, 실패 0, 오류 0, 건너뜀 0**. 실제 MySQL 8.0.46을 사용했습니다.

| 분류 | 개수 |
|---|---:|
| 기존 및 계산 엔진 단위·Controller | 107 |
| 외부 계약·통신 테스트 | 23 |
| 실제 DART 샘플 정규화·계산·중복 회귀 | 68 |
| 기존 Mock HTTP + MySQL | 6 |
| REAL 경로 + 가상 외부 서버 + 실제 MySQL | 6 |
| 기존 V1 데이터 → V2 마이그레이션 보존 | 1 |
| 합계 | 211 |

계약 테스트는 **공식 필드 구조로 직접 만든 가상 응답**을 사용했습니다.
이 가상 계약 자료 외에 **실제 API에서 수신한 별도 샘플 테스트 68개**를 추가했습니다.
[계약 자료](../src/test/resources/provider-fixtures/README.md)와 [실응답 자료](../src/test/resources/dart-live/README.md)를 구분합니다.

검증한 내용:

- ZIP/XML 상장 종목 매핑, XML 외부 엔티티 차단, 목록 재사용, 금융업 표시.
- 5개년 각각의 당기 금액, 공시 전 기간의 최신 확보 연도 선택, 전기값 미사용.
- CFS 우선·013 자료 없음에 한해서 OFS 대체, 호출 제한을 자료 없음으로 오인하지 않음.
- 정정 접수번호 불일치·기간 누락·중복 계정·잘못된 숫자·불명확한 보통주 EPS 처리.
- 콤마·괄호 음수·실제 0 보존, 원본·미매핑·접수번호 추적.
- 종목코드 정확 일치, 최신 시세 기준일·출처, 시세 없음·단일 item·비정상 종가 처리.
- 키·외부 오류 본문의 비노출, 응답 크기 제한, 본문 지연까지 포함한 타임아웃.
- DB 재사용·중복 없는 재수집·CFS에서 OFS 갱신·실패 시 기존 값 유지·stale·시세 없이 재무 지표 계산.
- V1의 기존 금액 123456789.123456과 접수번호가 V2 후에도 보존됨.

[테스트 요약·JAR SHA-256](phase-4-test-summary.json), [JUnit XML](test-results/phase4/TEST-com.stockinsight.RealDataIntegrationTest.xml).
DB 없이 test를 실행하면 198개가 실행되고 DB 테스트 13개는 건너뜁니다.
MySQL 통합 테스트는 README의 전용 DB 환경변수로 실행합니다.
마이그레이션 테스트는 별도의 **빈 테스트 DB**를 MYSQL_IT_UPGRADE_URL로 지정할 때만 실행합니다.

## 패키지 검증

bootJar 성공 후 stock-insight-backend-0.4.1.jar를 직접 실행했습니다.

1. MOCK 프로필: 기존 검색·재무·지표·우선주·기준 변경 동작 재확인.
   [MOCK 패키지 응답](phase-4-responses.json).
2. REAL 프로필, 키 없음: 서버 시작 및 503 DART_KEY_MISSING, meta.mode=REAL, Mock 대체 없음.
   [실제 키 누락 응답](phase-4-no-key-response.json).
3. REAL 프로필, 실제 키: 핵심 세 기업의 5개년·시세·sync·지표와 추가 9월 결산 기업 조회 확인.
   [실제 인증 호출 요약](phase-4-live-summary.json).

사용자 PC에서 열어 둔 브라우저 서버는 교체하지 않았습니다. 이번 서버는 별도 DB·포트로 실행하고 종료했습니다.
Mock 브라우저 실행 스크립트는 새 0.4.1 JAR을 가리킵니다. 재실행 전까지 기존 서버는 이전 버전입니다.
이번 단계의 새 REAL 화면 브라우저 검증은 수행하지 않았습니다.

## 실제 검증 대기 항목

| Phase 4 완료 기준 | 현재 상태 |
|---|---|
| 실제 기업 조회·저장·재조회·갱신 | 실제 호출·저장·sync 성공 |
| 실제 DART 응답 샘플 기반 정규화 테스트 | 실응답·독립 기대값 68개 통과 |
| 정정·전기 재작성 응답 구조 확인 | 실제 접수번호·비교 필드·수치 차이 및 불일치 보호 확인 |
| 실제 보통주 기본 EPS / 공시 BPS / 우선주 공시 | 일반 기업 공시 EPS·우선주 수 확인. 삼성 일반 EPS 범위 미확정은 N/A. BPS 관찰 범위 명시 |
| 12월 외 결산의 사업연도·실제 기간 관계 | 금비 9월 결산 실제 5개년 확인 |
| 실제 시세 활용 승인·응답·기준일 | 실제 V2 정상 응답·종가 기준일 확인 |
| 사용자 3종목 공시 수치 대조 | 미수행 |

키 설정 확인은 완료했습니다. [실제 수치 대조표](live-verification-table.md)를 준비했으며 사용자 직접 대조는 아직 미확인입니다.
수동 대조 결과를 확인한 뒤 Phase 4 전체 완료를 판정합니다.
현재 Phase 5 진행 승인을 요청하지 않습니다.

## 공식 명세 확인

- [고유번호 ZIP](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019018)
- [기업 개황](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019002)
- [전체 재무제표](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2019020)
- [주요계정·실제 기간 필드](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2019016)
- [주식 총수 현황](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS002&apiId=2020002)
- [금융위원회 주식시세정보](https://www.data.go.kr/data/15094808/openapi.do)

금융위원회 공식 페이지의 Swagger에서도 /getStockPriceInfo_V2 경로와 필드를 확인했습니다.
명세 확인과 실제 인증 응답 검증은 구분합니다.

## 학습 블록

직접 해볼 것: 키 없이 REAL 서버를 실행했을 때의 503 응답에서 ‘없는 데이터’와 ‘조회할 권한·설정 부족’을 어떻게 구분할지 설명해 보세요.

면접 질문과 답변 요점:

1. 외부 호출과 DB 쓰기를 왜 분리했나요? — 느린 네트워크 동안 트랜잭션·커넥션을 잡지 않고, 검증된 묶음만 원자적으로 저장하기 위해서입니다.
2. HTTP 200인 공급원 응답도 왜 status를 검사하나요? — DART는 호출 제한·인증 오류·자료 없음도 응답 본문의 상태 코드로 구분하기 때문입니다.
3. 실제 응답과 계약 테스트는 무엇이 다른가요? — 계약 테스트는 가정한 형태의 처리 능력을, 실응답 테스트는 실제 공급원과 그 가정의 일치 여부를 검증합니다.

커밋 제안: `feat: add DART and FSC providers with cached collection and source tracing`.
