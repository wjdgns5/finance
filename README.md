# Stock Insight 2.0

국내 상장사의 연간 재무자료와 투자지표를 조회하는 로컬 풀스택 프로젝트입니다. 기업 검색·선택 후 기업개요·재무정보·현금흐름·투자지표의 4개 탭에서 출처·회계기간·산출 조건을 함께 확인합니다.

현재 백엔드·프론트 버전은 **0.6.0**입니다. 실제 자료는 DART와 금융위원회 주식시세정보 V2를 사용하며, 키 없이 확인할 수 있는 MOCK 사례를 제공합니다.

## 구성

```text
outputs/
  README.md                    상세 시작 안내
  docs/                        SDD·설계 결정·검증·IDE 안내
  stock-insight-backend/        Java 21·Spring Boot·MySQL·Swagger
  stock-insight-frontend/       React·TypeScript·Vite
work/                          로컬 DB·실행 로그·캐시, Git 제외
```

기존 실행 스크립트와 문서 경로를 유지하기 위해 소스는 `outputs` 아래에 둡니다. 백엔드는 IntelliJ에서 `outputs/stock-insight-backend`, 프론트는 VS Code에서 `outputs/stock-insight-frontend`를 각각 엽니다.

## 기술과 실행

| 구성 | 기술 |
|---|---|
| 백엔드 |Java 21, Spring Boot 3.5.16, Gradle Wrapper, Spring Data JPA, Flyway |
| 프론트 |React 19, TypeScript, Vite, Tailwind CSS, Recharts |
| DB |MySQL. MOCK에서도 필요 |
| API 문서 |springdoc 2.10.0, Swagger UI, OpenAPI 3.0 |

현재 Windows 실행 스크립트는 설치된 MySQL Server 8.0·Java 21·Node 24 이상을 사용합니다. 다른 PC에서는 설치 경로와 문서에 있는 프로젝트 절대 경로를 맞춥니다. MySQL Workbench는 선택 도구입니다.

처음 받았다면 저장소 루트의 PowerShell에서 빌드합니다. Java 21이 JAVA_HOME에 설정되어 있어야 합니다.

```powershell
Set-Location '.\outputs\stock-insight-backend'
.\gradlew.bat bootJar
Set-Location '..\stock-insight-frontend'
npm.cmd ci
Set-Location '..\..'
```

가상 사례는 루트에서 아래 명령으로 켭니다. READY 후 [Mock 화면](http://127.0.0.1:5194/)을 열고 한빛 또는990001을 검색합니다. 사용하는 동안 실행 창을 열어 두고 종료할 때 Enter를 누릅니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File '.\outputs\stock-insight-frontend\scripts\start-phase5-local.ps1' -Mode Mock
```

실제 기업 조회는 백엔드의 `.env.real.example`을 `.env.real`로 복사해 서버에서 DART·FSC 키를 설정하고 같은 명령을 `-Mode Real`로 실행합니다. 키·DB 파일·node_modules·빌드 결과·로그는 저장소에 포함하지 않습니다. 기존 로컬 키 파일은 덮어쓰지 않습니다.

실행·종료·IDE 설정은 아래 문서를 따릅니다. Swagger는 프론트 포트가 아닌 백엔드에서 제공합니다. IntelliJ 기본은 [8080 Swagger](http://127.0.0.1:8080/swagger-ui.html), Real 일괄 실행은 [18095 Swagger](http://127.0.0.1:18095/swagger-ui.html)입니다.

## 문서

| 내용 | 문서 |
|---|---|
| 프로젝트 사용·빌드·기능 |[상세 README](outputs/README.md) |
| 소프트웨어 설계 명세 |[SDD](outputs/docs/SDD.md) |
| IntelliJ·VS Code·MySQL |[IDE 개발 안내](outputs/docs/ide-development-guide.md) |
| PowerShell 시작·종료 |[실행 안내](outputs/stock-insight-frontend/docs/local-run-guide.md) |
| Swagger UI·OpenAPI |[Swagger 안내](outputs/stock-insight-backend/docs/swagger-guide.md) |
| API·DB·금융 기준 |[API](outputs/stock-insight-backend/docs/api.md)·[DB](outputs/stock-insight-backend/docs/database.md)·[금융 정책](outputs/stock-insight-backend/docs/financial-policy.md) |
| 설계 이유·검증 |[설계 결정](outputs/docs/design-decisions.md)·[검증 안내](outputs/docs/verification.md) |
| GitHub에서 받은 뒤 사용 |[저장소 안내](outputs/docs/github-repository-guide.md) |

## 검증과 범위

Swagger 추가 후 **백엔드222개·실패/오류/건너뜀0**을 격리 MySQL에서 확인했습니다. 프론트21개·기존 브라우저15개·패키지 HTTP17개는 해당 단계의 기록입니다. Swagger 페이지·자원·모드별 명세는 HTTP로 확인했으며 자동 브라우저 연결 제한으로 Swagger 버튼 조작은 미확인입니다.

사용자의 기아·롯데케미칼·삼성전자 공시 직접 대조는 미확인입니다. 실시간 시세·자동 최신성 보장·분기/TTM·금융업 분석·사용자 인증·인터넷 공개 배포는 제공하지 않습니다. 금융 수치는 참고 정보이며 기준 연도·출처·N/A 사유와 함께 읽습니다.
