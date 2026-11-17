# Phase7 — 문서화 완료 보고

작성일:2026-11-17 (한국). 앱 버전0.6.0 유지. 문서와 재현 스크립트를 정리했으며 금융 계산·일반 앱 동작·DB 스키마를 변경하지 않았습니다.

## 완성한 문서

| 산출물 | 내용 |
|---|---|
| [프로젝트 README](../README.md) |목표·현재 상태·첫 준비·평소 실행·버전·화면·제한·문서 색인 |
| [PowerShell 실행 안내](../stock-insight-frontend/docs/local-run-guide.md) |필요한 창 수·3개 명령·시작 순서·종료·재부팅 후 재실행 |
| [백엔드 README](../stock-insight-backend/README.md) |빌드·환경변수·단독 실행 대안·API·전체 테스트 |
| [프론트엔드 README](../stock-insight-frontend/README.md) |구성·표시·상태 관리·개발·검증 연결 |
| [아키텍처](../stock-insight-backend/docs/architecture.md) |수집→정규화→저장→계산→화면, 트랜잭션·재사용·보안 경계 |
| [DB 명세·ERD](../stock-insight-backend/docs/database.md) |5개 Entity·컬럼·PK/FK·Unique·인덱스·V1/V2·갱신 |
| [API 명세](../stock-insight-backend/docs/api.md) |7개 요청·기본값·메타·숫자 문자열·상태·오류·sync |
| [금융 정책](../stock-insight-backend/docs/financial-policy.md) |5.3 공식·계정·기준,5.6 알려진 제한·정밀도·원본 추적 |
| [설계 결정](design-decisions.md) |선택 이유·대안·비용·확인 근거 |
| [포트폴리오 설명](portfolio.md) |30초/2분 설명·5분 시연·실제 문제 해결·면접 질문 |
| [검증 안내](verification.md) |테스트 명령·증거·자동/실제 공급원/브라우저/사용자 확인 구분 |
| [.env 예시](../stock-insight-backend/.env.example)·[REAL 예시](../stock-insight-backend/.env.real.example)·[프론트 예시](../stock-insight-frontend/.env.example) |값은 유지하고 읽는 스크립트·포트 적용 설명 보완 |

기존 문서에 남아 있던 실제 인증 대기·Phase4 구현 예정·0.4.1 JAR·211개 현재 테스트 등의 설명을 현재0.6.0 기준으로 갱신했습니다. 단계별 검증 보고서·응답·캡처는 당시 실행 증거로 보존합니다.

금융 기준은 정책5.3 한 곳에서 정의합니다. 공식·EPS/BPS 정책을 새로 바꾸지 않았습니다. 실제 시세 공급원은 금융위원회이며 KRX Client가 구현됐다고 쓰지 않습니다.

## 재현 스크립트·실행 확인

[verify-mysql.ps1](../stock-insight-backend/scripts/verify-mysql.ps1)을 추가했습니다. 일반 `gradlew test`와 달리 새로운 임시 MySQL·DB2개를 준비해 전체 검사를 수행합니다. 반복 실행에서도 Gradle이 이전 결과를 재사용하지 않도록 실제 작업을 재실행합니다. 앱에서 사용 중인 DB·키 파일은 사용하지 않습니다.

Windows PowerShell5.1에서 직접 실행해 **217개·실패0·오류0·건너뜀0**을 확인했습니다. 테스트 종료 후 자신이 시작한 DB를 중지하고 프로세스 환경변수를 복원합니다. 새 테스트에서 DART·FSC 실제 인증키는 필요하지 않습니다.

실행 요약과 JUnit 증거는 [Phase7 테스트 요약](../stock-insight-backend/docs/phase-7-test-summary.json)·[XML](../stock-insight-backend/docs/test-results/phase7/)에 보존합니다. 프론트엔드·일반 앱 코드는 바꾸지 않았으므로 Phase6의21개·빌드·HTTP17개·브라우저15개 결과를 그대로 참조합니다. Phase7에서 GUI를 새로 검증했다고 주장하지 않습니다.

## 문서 정합성 확인

- README·설정 예시의 버전·래퍼 포트·실제 스크립트 경로를 대조합니다.
- V1/V2 SQL과 DB 표·ERD의 테이블·키·새 컬럼을 대조합니다.
- API7개 매핑과 기본 파라미터를 Controller에 맞춥니다.
- 로컬 Markdown 링크·이미지·참조 코드 경로와 코드 블록의 PowerShell 구문을 확인합니다.
- 실제 API 키가 문서·소스·번들에 포함되지 않았는지 값 출력 없이 확인합니다.

검사 결과는 [문서 검사 요약](phase-7-checks.json)에 기록합니다. Mermaid는 Markdown 원문 다이어그램이며 실제 SQL·코드가 동작의 기준입니다.

검사 결과: Markdown24개·로컬 링크190개 정상, PowerShell 예제35개와 실행 스크립트3개의 Windows PowerShell5.1 구문 통과, API 매핑7개·DB 테이블5개·서로 다른 V1 컬럼명48개 대조, 실제 API 키 검출0개입니다. Mermaid3개는 원문과 참조 경로를 확인했으며 별도 이미지 렌더 검증은 하지 않았습니다.

## 완료 범위·남은 확인

Phase7 문서 정리와 현재 PC용 재현 절차를 완료했습니다. 새 PC 전체 설치·Docker 엔진·다수 브라우저·장시간 부하는 이번 단계에서 새로 실행하지 않았습니다. 로컬 서버를 중단하거나 키 설정을 덮어쓰지 않았습니다.

**사용자의 기아·롯데케미칼·삼성전자 공시 원문 대조는 미확인입니다.** [수치 대조표](../stock-insight-backend/docs/live-verification-table.md)와 [대조 방법](../stock-insight-backend/docs/manual-verification.md)으로 확인할 수 있습니다. 따라서 사용자 수동 검증을 포함한 전체 최종 조건까지 모두 충족했다고 판정하지 않습니다.

## 학습 블록

직접 해볼 것: README만 열고 Real 서버 시작→기업 선택→공시 출처 확인→Enter 종료→재실행을 순서대로 해보세요. 이어서 ADR 한 항목을 골라 대안과 비용을 본인의 말로 설명하세요.

면접 예상 질문과 답변 요점:

1. **문서에서 금융 공식 정의를 한 곳에 모은 이유는?** README·API·설계 문서가 서로 다른 계산 정책을 갖지 않게 하고 변경 영향과 검증 기준을 추적하기 위해서입니다.
2. **통합 테스트 실행 스크립트를 추가한 이유는?** DB 없는 테스트 성공과 MySQL·마이그레이션까지 포함한 성공을 구분하고, 새 임시 DB에서 같은 검사를 재현하기 위해서입니다.
3. **포트폴리오에서 검증 한계를 어떻게 설명하나요?** 실제로 실행한 자동·실데이터·브라우저 결과에 근거를 연결하고 사용자 공시 대조·부하·배포 등 미확인 범위를 구분합니다.

커밋 제안: `docs: complete runtime, data policy and portfolio documentation`

다음으로 진행할 수 있는 작업은 사용자 공시 대조 결과 기록과 확인된 개선 요청입니다. 별도 요청 없이 금융 정책·배포 범위를 확장하지 않습니다.
