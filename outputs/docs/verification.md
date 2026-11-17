# 실행·테스트·검증 상태

현재 런타임 버전은0.6.0입니다. 자동 테스트, 실제 공급원 수집, 브라우저 검증, 사용자 공시 대조를 구분합니다. Phase7은 문서와 재현 스크립트를 정리하는 단계이며 투자 판단 검증은 포함하지 않습니다.

이후 Swagger UI·OpenAPI를 추가하고 **백엔드222개·실패/오류/건너뜀0**을 별도로 재확인했습니다. 기존217개에 문서 계약·UI 자원 제공·접근 정책5개 검사를 추가했습니다. [Swagger 안내](../stock-insight-backend/docs/swagger-guide.md)·[실행 기록](../stock-insight-backend/docs/swagger-verification.json)을 참조하세요. 프론트 화면 코드는 변경하지 않아 기존21개 결과를 유지합니다.

## 프론트엔드

```powershell
Set-Location 'C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend'
npm.cmd ci
npm.cmd test
npm.cmd run build
```

의존성이 이미 준비돼 있고 package-lock.json이 바뀌지 않았다면 `npm.cmd ci`를 매번 실행하지 않습니다. Phase6에서21개 테스트·TypeScript 검사·프로덕션 빌드가 통과했습니다. Phase7에서 앱 코드는 바꾸지 않았으므로 해당 결과를 이어 사용합니다.

테스트는 Node 내장 도구입니다. API 응답 구조·모드·취소·부분 실패, 숫자 문자열·BigInt 표시, 기준·기간·접수번호 일치, 차트 결측과 실제0을 검사합니다.

## 백엔드 전체 — MySQL 포함

현재 PC의 설치된 MySQL8.0·Java21을 사용합니다. API 키·Docker가 필요하지 않습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend\scripts\verify-mysql.ps1"
```

스크립트는 테스트용13407 포트와 매 실행마다 새 DB 파일 경로를 사용합니다. 전용 DB2개를 만든 뒤 Gradle 테스트를 실제로 재실행하고, 실패·오류·건너뜀 없는지 JUnit XML을 확인합니다. 완료 시 자신이 시작한 DB를 종료하고 프로세스 환경변수를 복원합니다. 앱의 Mock·Real·QA DB는 사용하지 않습니다.

정상 종료 문구는 `PASS: 222 tests; no failures, errors or skips.`입니다. 실행 로그는 프로젝트 작업 폴더의 `work/verification-tests/<실행 식별자>`에, Gradle 보고서는 백엔드 `build/reports/tests/test/index.html`에 생성됩니다.13407이 사용 중이면 `-MySqlPort 13408`을 추가할 수 있습니다.

백엔드 폴더에서 다음 명령으로 DB 없는 단위·웹 계층 테스트도 실행할 수 있습니다.

```powershell
.\gradlew.bat test
```

단, `MYSQL_IT_URL`·`MYSQL_IT_UPGRADE_URL`이 없으면 MySQL 통합·마이그레이션 클래스가 건너뛰어집니다. 이 명령만으로 전체 검사를 수행했다고 보고하지 않습니다. 통합 테스트는 테스트 행을 삭제·추가하므로 격리된 제공 스크립트로 실행합니다.

| 검사 영역 | 검증하는 내용 |
|---|---|
| FinancialMetricCalculatorTest | 정상·0·음수·NULL·분모 비양수·기간·단위·주식수·기준 변경 |
| LiveDartSamplesTest | 저장된 실제 DART 응답의 정규화·계산·원본 추적68개 사례 |
| MockApiIntegrationTest | Spring Boot·MySQL·Flyway·전체 HTTP 흐름·지표 비저장 |
| RealDataIntegrationTest | 로컬 upstream 서버를 이용한 수집·DB 재사용·sync·중복 방지·실패 보존 |
| MigrationUpgradeTest | V1 자료를 V2로 올려 금액·접수번호를 보존 |
| LocalApiPolicyTest | 외부 웹 요청 차단·로컬 요청 허용·내부 오류 비노출 |

REAL 통합 테스트의 upstream은 로컬 가짜 서버입니다. 실제 외부 인증 검증은 아래 실데이터 증거와 구분합니다.

## 브라우저 재현

평소 시작·종료·재부팅 후 실행은 [PowerShell 안내](../stock-insight-frontend/docs/local-run-guide.md)를 따릅니다. 정상 Mock·Real 화면은5194·5195입니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-quality-check.ps1"
```

READY 뒤 [QA 제어](http://127.0.0.1:5206/__quality)에서 정상·부분 실패·이력 실패·접수번호 불일치·구조 오류·stale·자료 없음·연결 끊김·지연·최초 REAL 오류를 선택합니다. 이 실패는 QA 프록시에서만 의도적으로 주입한 것입니다.

Phase6에서15개 브라우저 시나리오와17개 패키지 HTTP 검사를 확인했습니다. 정상 검색→선택→4탭,390px 내부 표 스크롤, 늦은 이전 응답 차단, 기존 오류 화면에서 재시도 복구가 포함됩니다. [상세 결과·화면](../stock-insight-frontend/docs/phase-6-verification.md)을 참조하세요.

## 실제 공급원·사용자 수동 검증

실제 DART·금융위원회 인증과 수집은 확인했습니다.0.6.0 패키지로 기아·롯데케미칼·삼성전자·금비 각각5개년 재무·최신 지표·연도별 이익률 HTTP 회귀를 확인했습니다. 이는 저장된 실제 응답과 구현의 검사이며 실제 공시 화면을 사용자가 읽은 결과는 아닙니다.

사용자 대조 대상은 일반 제조업 기아, 적자 롯데케미칼, 우선주 발행 삼성전자입니다. [대조표](../stock-insight-backend/docs/live-verification-table.md)와 [순서 안내](../stock-insight-backend/docs/manual-verification.md)를 사용합니다. **현재 상태는 미확인**입니다. 일치·차이 항목을 알려주면 별도 결과로 기록합니다.

## 근거와 남은 범위

| 구분 | 결과·근거 |
|---|---|
| Swagger 추가 후 백엔드 |222개 통과, [실행 기록](../stock-insight-backend/docs/swagger-verification.json)·[XML](../stock-insight-backend/docs/test-results/swagger/) |
| Phase6/7 당시 백엔드 |217개 통과, [Phase6 XML](../stock-insight-backend/docs/test-results/phase6/) |
| 프론트엔드 |21개 통과·빌드 성공, [요약](../stock-insight-frontend/docs/phase-6-test-summary.json) |
| 패키지 HTTP |17개 통과, [실행 기록](../stock-insight-frontend/docs/phase-6-http-verification.json) |
| 브라우저 |15개 통과, [실행 기록](../stock-insight-frontend/docs/phase-6-browser-verification.json) |
|0.6.0 실데이터 HTTP |4개 기업, [응답](../stock-insight-backend/docs/phase-6-real-responses.json) |
| npm audit |당시 취약점0개, [결과](../stock-insight-frontend/docs/phase-6-npm-audit.json) |
| API 키 포함 여부 |소스·번들·문서·해당 서버 로그에서 실제 키 검출0, [범위](../stock-insight-frontend/docs/phase-6-security-audit.json) |
| 사용자 공시 대조 |미확인 |
| 실제 휴대전화·다수 브라우저·장시간 부하 |전체 검사 미수행 |
| Java 전체 의존성 CVE 검사 |별도 SCA 도구로 미수행 |
| Docker 엔진 경로 |현재 PC에서는 엔진 미가동으로 미검증. native MySQL 경로로 검증 |

과거 단계의 테스트 개수는 그 단계 당시 결과입니다. 현재 집계와 구분합니다. Phase7의 문서·스크립트 검사는 [문서화 완료 보고](phase-7-documentation.md)에 기록합니다.
