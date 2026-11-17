# Phase 6 — 통합·품질 검증

검증일: 2026-11-17 (한국). 버전: 프론트엔드·백엔드 0.6.0.

**구현·자동 테스트·패키지 HTTP·프로덕션 브라우저 검증을 완료했습니다.** 사용자 공시 원문 직접 대조는 미확인 상태를 유지합니다. Phase 7은 사용자 확인 후 진행합니다.

## 수정한 문제

| 문제 | 수정·검증 |
|---|---|
| 외부 Origin의 단순 POST가 로컬 수집 API에 도달 | 수정 전 보안 테스트에서 재현. API 요청의 Host·Origin·Sec-Fetch-Site를 검사해 외부 요청은 403으로 처리. 로컬 CLI·로컬 프록시는 유지 |
| 잘못된 지표 JSON 구조가 화면 순회 로직에 도달 | HTTP 경계에서 메타·배열·금액 문자열 구조 검사. 지표 오류는 해당 영역 N/A·오류로 표시하고 재무 요약 유지 |
| 최초 REAL 오류의 모드 정보가 사라짐 | ApiError에 검증된 meta 보존, REAL 배지·검색 결과·인증 오류 표시. 가상 기업 안내를 선행 표시하지 않음 |
| 자료 없는 투자지표에 어색한 연도 문구 | `재무 연도 미확인`으로 표시. 임의 사업연도를 생성하지 않음 |
| 환경파일 변형 이름 누락 가능 | `.env.*`를 Git 제외하고 예시 파일만 예외. 실제 API 키의 소스·산출물 포함 여부 별도 검사 |

금융 공식·EPS/BPS 정책·연간 범위·DB 스키마는 변경하지 않았습니다. LocalApiPolicy는 로컬 개발 앱의 요청 경계이며 사용자 인증을 구현한 것은 아닙니다. CORS 허용 범위를 확대하지 않았습니다.

## 자동·HTTP 통합 검증

| 구분 | 직접 실행 결과 | 증거 |
|---|---|---|
| 프론트엔드 테스트 | 21개 통과 | API 계약·부분 실패·REAL 오류 모드·취소·금액·기간·기준 검사 |
| 프론트엔드 빌드 | TypeScript·Vite 통과 | 메인271.44 kB/gzip85.08, 차트394.96 kB/gzip112.77, CSS34.86 kB |
| 백엔드 전체 | 217개, 실패0·오류0·건너뜀0 | [JUnit XML](../../stock-insight-backend/docs/test-results/phase6/) |
| 패키지·MySQL·Flyway | 통과 | [Mock 응답](../../stock-insight-backend/docs/phase-6-responses.json), [REAL 키 없음](../../stock-insight-backend/docs/phase-6-no-key-response.json) |
| 패키지 보안·QA 프록시 | 17개 HTTP 검사 통과 | [HTTP 결과](phase-6-http-verification.json) |
| npm 보안 공지 | 취약점0개 | [npm audit 결과](phase-6-npm-audit.json) |
| 키 포함 여부·실제 응답 계약 | 설정된 API 키 검출0, 실제 응답 계약12개 통과 | [보안·계약 검사](phase-6-security-audit.json) |
| 0.6.0 실제 저장 자료 HTTP | 기아·롯데케미칼·삼성전자·금비 각각5개년 재무·최신지표·이익률 이력 통과 | [REAL 회귀 결과](../../stock-insight-backend/docs/phase-6-real-responses.json) |

백엔드 전체 검사에는 계산 엔진, 실제 DART 샘플 정규화, 실제 MySQL 조회·저장·재수집·실패 시 보존, V1→V2 마이그레이션 보존, 로컬 API 경계 검사가 포함됩니다. 테스트용 upstream 서버를 이용한 REAL 통합과 실제 공급원에서 수집한 자료의 검증은 구분합니다.

오류 응답에 내부 JDBC 주소·예외 메시지·인증 텍스트가 나오지 않는지 확인했습니다. API는 127.0.0.1에 바인딩하며 응답에 `X-Content-Type-Options: nosniff`를 추가합니다. 외부 Host·Origin·opaque Origin·cross-site 요청은 수집/조회 전에 차단합니다. 로컬 PowerShell의 Origin 없는 요청과 로컬 화면 프록시는 정상입니다.

프로덕션 HTML·해시 자산200, QA 정적 서버의 `.env`·`/@fs` 경로403을 확인했습니다. Vite 개발 서버는 기존 loopback 바인딩·기본 allowedHosts·CORS·파일 접근 제한을 유지했습니다. npm audit 결과는 해당 시점의 npm 의존성 검사이며 Java 전체 의존성의 취약점 0을 보증하는 결과는 아닙니다.

## 실제 브라우저 검증

PC PowerShell의 `start-quality-check.ps1`로 설치된 MySQL·0.6.0 패키지 백엔드·프로덕션 `dist`를 실행했습니다. 테스트 전용 QA 프록시는 5206, 백엔드는18096, DB는13396입니다. 프록시는 백엔드 Mock 응답에만 오류를 주입하며 DB 원본·금융 계산·일반 실행 경로를 변경하지 않습니다. 화면 오른쪽 아래 검증 전용 표시가 있습니다.

| 시나리오 | 직접 확인 결과 |
|---|---|
| 정상 전체 흐름 | 기업명·코드·Enter 검색→선택→KPI→4탭. 지연 로드 차트·표 정상 |
| 최신 지표만503 | 매출·영업·순이익 요약 유지, 지표 N/A·오류·재시도 |
| 이익률 이력만504 | 매출 막대 유지, 이익률 실패 안내. 선을0으로 채우지 않음 |
| 공시 접수번호 불일치 | 해당 지표를 함께 표시하지 않고 기준 불일치 안내 |
| 지표 배열 구조 오류 | 형식 오류 표시·재무 요약 유지, 빈 화면으로 중단되지 않음 |
| 마지막 정상 자료 | 기존 값·기준일과 수집 실패 안내 유지 |
| 연간 자료 없음 | 확보0개년, 요약·지표 N/A, 재무/현금 차트 자료 없음, 연도 미확인 |
| API 연결 끊김 | 연결 오류·재시도, 임의 수치·가상 목록 대체 없음 |
| 최초 REAL 키 오류 | REAL 배지·검색 결과·인증키 설정 오류, Mock 대체 없음 |
| 늦은 이전 응답 | 한빛 응답2.5초 지연 중 새봄 선택. 지연 종료 후에도 새봄·영업−80억·EPS−600 유지 |
| 390px·자료 부족 | 문서375px/뷰포트390px, 표307px/내용590px 내부 스크롤. CFS/OFS 차트 분리 |
| 통신 복구 후 재시도 | 별도 제어 탭에서 복구 후 기존 오류 화면의 재시도로 목록→정상 대시보드 복구 |
| 금융업 선택 | 미지원 안내, 이전 KPI0개 |
| 실제 화면 회귀 | 기존 REAL5195에서 새 프론트엔드 계약 검사로 기아5개년·4탭·출처·지표 정상 |
| 검색 결과 없음·공백 | 결과0개·목록 복구, 공백은 입력 안내 |

[브라우저 실행 기록](phase-6-browser-verification.json)에 15개 결과를 저장했습니다. REAL5195는 사용자가 열어 둔 기존 백엔드이며 새 프론트엔드 코드의 호환성을 확인했습니다. 새 백엔드0.6.0은 독립 패키지 HTTP와 실데이터 저장 자료로 별도 확인했습니다.

## 재현 방법

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-quality-check.ps1"
```

READY 후 [QA 제어](http://127.0.0.1:5206/__quality)에서 시나리오를 선택합니다. 정상 API 데이터는 모두 가상임을 명시합니다. 실제 인증 오류와 자료 없음 등을 의도적으로 주입하는 검사이며 실제 공급원 장애가 발생했다고 의미하지 않습니다. 통신 복구·재시도는 별도 제어 탭으로 시나리오를 정상으로 바꾼 뒤 원래 오류 화면에서 재시도를 누르면 됩니다.

종료는 실행한 PowerShell 창에서 Enter입니다. 자신이 시작한 프로세스만 중지하며 DB 폴더는 보존합니다. 이전0.5.0 서버는 재실행 전까지 기존 JAR로 동작합니다. 일반 실행 스크립트는0.6.0 JAR로 갱신했습니다.

## 알려진 제한·남은 확인

| 항목 | 상태 |
|---|---|
| 사용자3종목 DART 공시 화면 직접 대조 | **미확인** — [대조표](../../stock-insight-backend/docs/live-verification-table.md). 자동 검사로 사용자 확인을 대신하지 않음 |
| 실제 휴대전화 터치·보조공학·복수 브라우저 전체 | 미검증. PC 내장 브라우저·키보드·390px 검증과 구분 |
| 대량 동시 사용자·장시간 부하 | 범위 밖. 실제 수집 서비스의 synchronized 호출은 단일 로컬 앱 기준 |
| 최신 정정 공시·최신 거래일 보장 | 기존 정책 유지. 저장 자료 재사용, 필요 시 로컬 sync. 불확실한 공시·EPS·우선주 계산은 N/A 또는 명시 오류 |
| Java 의존성 전체 CVE 검사 | 별도 SCA 도구로 수행하지 않음. 이번 검사는 설정·요청 경계·민감정보·예외·현재 npm 공지 범위 |
| 배포·사용자 인증 | 구현하지 않음. 로컬 실행 범위 유지 |
| Phase7 전체 문서화 | 사용자 확인 후 시작 |

## 학습 블록

직접 해볼 것: QA 제어에서 API 연결을 끊고, 다른 탭에서 정상으로 돌린 뒤 원래 화면의 재시도를 눌러 보세요. 컴포넌트 상태·HTTP 요청·DB 중 어느 부분이 복구되는지 설명해 보세요.

면접 질문과 답변 요점:

1. CORS만으로 로컬 수집 API 호출을 막을 수 있나요? — 응답 읽기 제한과 요청 실행은 구분해야 합니다. 외부 Origin의 단순 POST를 재현하고 서버 진입 전에 차단했습니다.
2. TypeScript가 있는데 JSON 검사가 필요한 이유는? — 서버 응답은 실행 시 들어오는 데이터입니다. 배열·숫자 문자열·메타 구조를 확인해야 잘못된 응답이 화면을 중단시키지 않습니다.
3. 늦게 도착한 이전 응답은 어떻게 처리하나요? — 이전 AbortController를 취소하고 결과 반영 전에 취소 상태를 확인합니다. 실제 지연 시나리오로 최종 선택 기업이 유지되는지 확인했습니다.

커밋 제안: `fix: harden local API access and validate dashboard failure recovery`

참고: [Spring MVC CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html), [Vite 서버 보안 옵션](https://vite.dev/config/server-options).

## 화면 증거

- [프로덕션 정상](screenshots/phase6-production-normal.jpg)
- [지표 부분 실패](screenshots/phase6-partial-error.jpg)
- [연결 끊김](screenshots/phase6-offline.jpg)
- [REAL 인증 오류](screenshots/phase6-real-auth-error.jpg)
- [지연 중 기업 전환](screenshots/phase6-delayed-selection.jpg)
- [프로덕션390px](screenshots/phase6-production-mobile.jpg)
- [실제 기아 회귀](screenshots/phase6-real-regression.jpg)
