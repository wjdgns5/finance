# Phase 3 — 지표 계산 엔진 완료 보고

검증일: 2026-11-17, Asia/Seoul. 패키지 버전: 0.3.0.
2026-11-17 사용자 답변의 ‘조건 충족 시 계산’을 BPS 정책에 반영했습니다.
금융 기준은 [금융지표 기준표](financial-policy.md)를 참조합니다.

## 목표와 설계

최신 확보 연간 사업연도의 10개 지표를 계산하는 GET /api/v1/companies/{stockCode}/metrics를 추가했습니다.
Controller → 읽기 전용 MetricQueryService → 기존 CompanyQueryService → Repository → MySQL로 조회하고,
FinancialMetricCalculator가 응답 DTO의 값을 BigDecimal로 계산합니다. Entity와 HTTP 응답은 분리합니다.
계산용 내부 모델을 추가하거나 결과를 DB에 저장하지 않았습니다. 스키마·Flyway V1·기존 Mock 수치는 변경하지 않았습니다.
기존 검색·기본정보·financials 응답도 유지했습니다. 프론트엔드 지표 화면은 Phase 5 범위입니다.

## 파일과 역할

기준 폴더: `C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend`.

| 구분 | 파일 | 역할 |
|---|---|---|
| 신규 | [MetricModels.java](../src/main/java/com/stockinsight/api/MetricModels.java) | 지표 응답·단위·사유 코드. 상태는 기존 DataStatus 재사용 |
| 신규 | [FinancialMetricCalculator.java](../src/main/java/com/stockinsight/service/FinancialMetricCalculator.java) | 금융 계산·입력 검사·N/A 사유·설명 |
| 신규 | [MetricQueryService.java](../src/main/java/com/stockinsight/service/MetricQueryService.java) | 최신 연도·정확한 전년 선택, 공시·주가 메타데이터 유지 |
| 신규 | [MetricController.java](../src/main/java/com/stockinsight/api/MetricController.java) | 종목코드 검증과 GET 지표 API |
| 신규 | [FinancialMetricCalculatorTest.java](../src/test/java/com/stockinsight/service/FinancialMetricCalculatorTest.java) | 사전 기대값 60개와 추가 경계값 검사 |
| 신규 | [MetricQueryServiceTest.java](../src/test/java/com/stockinsight/service/MetricQueryServiceTest.java) | 빈 자료, 연도 간격, 메타데이터 검사 |
| 신규 | [MetricControllerTest.java](../src/test/java/com/stockinsight/api/MetricControllerTest.java) | 400·404·422 응답 검사 |
| 수정 | [MockApiIntegrationTest.java](../src/test/java/com/stockinsight/MockApiIntegrationTest.java) | 실제 MySQL·HTTP 지표 검증과 기존 테스트 유지 |
| 수정 | [build.gradle](../build.gradle) | 버전 0.3.0. 실행 중일 수 있는 구버전 JAR과 분리 |

계산기는 필수값을 검증한 뒤에만 나눗셈을 수행합니다. 한 지표가 N/A여도 나머지 지표 계산은 계속합니다.
EPS는 공시값 그대로 유지하고 PBR에는 반올림한 BPS를 재사용하지 않습니다.
원본 계정의 공시 식별정보는 financials API에, 사용 보고서와 주가의 기준일·출처는 응답 meta에 남깁니다.

## 자동 검증 결과

실제 MySQL을 포함한 Gradle test: **113개 통과, 실패 0, 오류 0, 건너뜀 0**.

| 테스트 | 통과 |
|---|---:|
| 기존 CompanyQueryServiceTest | 9 |
| 기존 CompanyControllerTest | 5 |
| FinancialMetricCalculatorTest | 89 |
| MetricQueryServiceTest | 2 |
| MetricControllerTest | 2 |
| MockApiIntegrationTest | 6 |
| 합계 | 113 |

[사전 기대값 문서](phase-3-expectations.md)와 [CSV](../src/test/resources/metric-expectations.csv)는 엔진 작성 전에 고정했습니다.
10개 공식마다 정상·음수·0·NULL·자료 부족·비정상 입력을 비교했습니다.
추가 검증은 비양수 분모, 주식수·기준일·공시 출처 불일치, 우선주 발행·확인 불가,
연결/별도 전환, 전년 누락, 별도 BPS, 중복 계정·단위·통화·기간 오류,
중간 반올림 방지, 시세 없이 재무 지표 유지, 설명·단위·기준일 제공을 포함합니다.

MySQL 8.0.46의 별도 전용 DB에서 Flyway와 JPA validate를 실제 실행했습니다.
정상 가상 기업의 10개 수치, 적자의 음수 EPS와 PER N/A, 우선주의 BPS·PBR N/A,
자료 부족 기업의 기준 변경·공시 EPS 누락·시세 누락을 HTTP로 검증했습니다.
반복 조회가 계정·수집 이력을 추가하거나 지표 테이블을 만들지 않는 것도 확인했습니다.

`bootJar` 성공 후 **stock-insight-backend-0.3.0.jar를 별도로 실행**했습니다.
새 API 네 가지 기업 응답과 기존 검색·5개년 재무 응답을 다시 확인하고
[실제 실행 응답 JSON](phase-3-responses.json)에 저장했습니다. 해당 데이터는 모두 가상 MOCK입니다.
이 응답 파일은 Phase 4 작업 중 보관된 0.3.0 JAR을 다시 실행해 갱신했습니다.
Phase 4의 0.4.0 JAR 응답은 별도 파일에 보존합니다.
테스트 JUnit XML은 build/test-results/test, HTML 보고서는 build/reports/tests/test/index.html에 생성됩니다.

## 실행과 수동 확인

JDK 21 환경에서 백엔드 폴더를 열고:

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

기본 test는 DB를 요구하지 않는 107개를 실행하며 MySQL 테스트 6개는 건너뜁니다.
실제 DB 통합 테스트를 재현할 때는 README의 MYSQL_IT_URL·MYSQL_IT_USER·MYSQL_IT_PASSWORD를
전용 테스트 DB로 설정합니다. 이번 검증에서는 이 여섯 테스트도 모두 실행했습니다.

실행 방법은 [백엔드 README](../README.md)를 따릅니다. 백엔드 기본 포트가 8080일 때:

```powershell
$base = 'http://127.0.0.1:8080/api/v1/companies'
(Invoke-RestMethod ($base + '/990001/metrics')).data.metrics |
    Format-Table code,value,status,reasonCode
(Invoke-RestMethod ($base + '/990003/metrics')).data.metrics |
    Where-Object { $_.code -in @('BPS','PBR') } |
    Format-Table code,value,status,reasonCode
```

정상 기업은 10개 지표가 AVAILABLE, 우선주 기업의 BPS·PBR은 null + NOT_APPLICABLE + PREFERRED_SHARES여야 합니다.
990004 증가율은 BASIS_MISMATCH, 990002 PER은 NON_POSITIVE_EPS를 확인합니다.
오류 시 DB 연결·Java 21·실행 JAR 버전을 먼저 확인합니다. 404 RESOURCE_NOT_FOUND라면 구버전 서버인지 확인합니다.

브라우저 검증용 스크립트도 새 0.3.0 JAR을 가리키도록 갱신했습니다.
이미 실행 중인 구버전 서버는 자동 교체하지 않았습니다. 사용자 터미널에서 기존 실행을 종료하고
기존 start-browser-check.ps1을 다시 실행하면 새 버전이 적용됩니다. 기존 DB 자료는 유지합니다.
이 보고서의 검증 서버는 사용자 실행 서버와 다른 DB·포트로 실행한 뒤 종료했습니다.

## 완료 범위와 남은 확인

- 구현·단위·통합·패키지 HTTP 검증 완료. 다음 단계로 넘어가도 좋습니다.
- 사용자 직접 수치 대조 결과는 아직 받지 않았습니다. 실제 DART 3종목 수동 대조는 Phase 4입니다.
- 이번 단계는 백엔드 API입니다. 새 지표의 브라우저 카드·툴팁·4탭은 아직 연결하지 않았습니다.
- 실제 공급원, 공시 기본 EPS 식별, 우선주 공시, 정정·재작성, 시세 접근권한은 Phase 4에서 확인합니다.
- 알려진 금융 제한은 [금융지표 기준](financial-policy.md)에 기록했습니다.
- Phase 4는 사용자 확인 전 시작하지 않습니다.

## 학습 블록

직접 해볼 것: 990003/metrics에서 BPS의 reasonCode를 찾고, ‘미수집’과 ‘산출 불가’가 왜 다른지 설명해 보세요.
힌트: 공시 주식수와 우선주 여부가 이미 있는지 먼저 살펴보세요.

면접 예상 질문과 답변 요점:

1. 왜 BigDecimal을 쓰고 최종 단계에서만 반올림하나요? — 이진 부동소수점 오차와 중간 반올림 누적을 피하기 위해서입니다. PBR이 대표 사례입니다.
2. 왜 지표를 DB에 저장하지 않나요? — 원본 공시가 갱신되면 현재 저장값으로 다시 계산해 파생값 불일치를 줄이기 위해서입니다.
3. 왜 N/A를 0으로 반환하지 않나요? — 실제 0과 자료 부족·산출 불가의 의미가 다르므로 상태와 사유를 함께 전달해야 하기 때문입니다.

커밋 제안: `feat: add annual financial metric engine with explicit NA reasons`.
