# Phase 1 검증 보고

검증일: 2026-11-17, Asia/Seoul.
기존 소스 미제공으로 새 백엔드 기반을 작성했습니다. 기존 프로젝트와의 호환·회귀 검증을 수행했다는 뜻은 아닙니다.

## 구현 완료

- Java 21 / Spring Boot 3.5.16 / Gradle Wrapper 8.10.2 기반.
- 5개 Entity, Repository, Service, Controller, 공급원 인터페이스와 Mock 구현.
- 기업 검색·기본정보·연간 재무제표 조회, 공통 성공·오류 응답.
- 모드·출처·연도·연결/별도·접수번호·공시일·수집일·시세 기준일 메타데이터.
- Flyway V1 및 `ddl-auto=validate`.
- 가상 5개 기업, 4개 기본 Mock 사례와 금융업 미지원 사례.
- 결측 null·정상 0·음수 보존, 연결 우선 선택, 수집 실패 이력의 stale 표시.
- 실행 스크립트, MySQL Compose 설정, ERD·DB·API·README.

## 자동 테스트

서비스 단위 테스트 9개, Controller 테스트 5개: **14개 통과**.

검증 내용: 모드가 적용된 검색, 검색 와일드카드 이스케이프, 없는 기업 404,
연결 우선 선택, 5개 사업연도 중복 제거, 연도별 기준 변경·3월 결산 보존,
누락·음수·0 구분, 미지원 업종 422, 빈 재무·빈 시세 상태, 최신 실패 이력,
필수 검색어·코드 형식·연도 범위·자료형 검증.

## 통합 테스트

실제 **MySQL 8.0.46** + Spring Boot RANDOM_PORT HTTP 테스트 3개: **3개 통과**.
JPA Repository나 DB를 Mock으로 교체하지 않았습니다.

1. Flyway V1 실행 및 JPA 매핑 검사 후 검색 → 기본정보 → 5개년 재무 HTTP 흐름 성공.
   DB 저장 매출과 사전 지정 값 일치, meta 및 금액 문자열 확인.
2. 데이터 부족과 400·404·422 상태 구분, SQL LIKE 특수문자 검색 확인.
3. Mock 적재 재실행 시 기업·수집 이력 중복 없음 확인.

실행 명령은 전용 임시 DB에 MYSQL_IT_URL/USER/PASSWORD를 설정한 후 `gradle test bootJar`였습니다.
이때 **전체 17개 통과 / 실패 0 / 오류 0 / 건너뜀 0**입니다.
원본 JUnit 결과는 [MySQL 실행 XML](test-results/mysql/)에 보존했습니다.

패키징한 `stock-insight-backend-0.1.0.jar`를 별도 실행해 다음 HTTP 호출도 성공했습니다.

- 기업 검색 및 기본정보.
- 정상 기업 재무 5개년.
- 부족 기업 재무 2개년과 메타데이터.

실제 응답: [verification-responses.json](verification-responses.json).
수치는 전부 Mock이며 실제 공시 대조 자료가 아닙니다.
테스트에 사용한 앱과 임시 MySQL 프로세스는 확인 후 중지했습니다.

## 실행 도구 검증

- Wrapper가 Gradle 8.10.2 ZIP을 다운로드하고 고정한 SHA-256을 검사한 뒤 실행: 성공.
- DB 환경변수 없이 `gradlew.bat test bootJar`: 성공.
  서비스·Controller 14개 통과, MySQL 통합 3개는 명시적으로 건너뜀.
- 실행 JAR 생성: 성공.
- `docker compose config --quiet`: 구성 문법 검사. 엔진 실행 검증과는 별개입니다.

## 사용자 수동 검증

사용자가 직접 수행한 결과는 아직 없습니다. [API 문서의 체크리스트](api.md)를 제공합니다.
실제 DART 공시 3종목 대조는 Phase 4 대상입니다.

## 아직 검증하지 못한 항목

- Docker 엔진 및 Compose의 MySQL **8.4.13 컨테이너 실행**.
  CLI는 설치되어 있지만 엔진 연결이 되지 않아, 설치된 MySQL 8.0.46을 작업용 임시 데이터 경로에서 사용했습니다.
- 기존 Stock Insight 코드 재사용과 기존 기능 회귀 검증.
- 프론트엔드 검색 화면·KPI 표시: Phase 2.
- 지표 계산과 계산 기준별 N/A: Phase 3.
- 외부 API, 실제 응답 정규화, EPS·BPS 수집, 정정 공시 대조·재수집: Phase 4.

## 알려진 문제와 다음 작업

현재 Mock 범위에서 실패한 테스트나 확인된 API 오류는 없습니다.
Docker 실행은 이 환경에서 확인하지 못했으므로 MySQL 8.4 실행 검증 완료로 표시하지 않습니다.
BPS 계산 문구 해석은 계산 엔진 구현 전에 사용자와 확정합니다.
다음 작업은 사용자 확인 후 Phase 2의 React/TypeScript 세로 슬라이스입니다.

커밋 제안: `feat: add phase 1 mock financial APIs with MySQL persistence`.
