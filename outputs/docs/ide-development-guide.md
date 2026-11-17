# IntelliJ 백엔드·VS Code 프론트 실행

현재 Windows PC에서 **실제 기업 조회용 개발 환경**을 실행하는 방법입니다. 먼저 한 번 설정하고, 이후에는 DB → 백엔드 → 프론트 순서로 켭니다. 기존 [일괄 실행](../stock-insight-frontend/docs/local-run-guide.md)은 그대로 사용할 수 있습니다.

## 필요한 프로그램과 역할

| 프로그램 | 역할 | 현재 PC |
|---|---|---|
| IntelliJ IDEA | Java 백엔드 편집·실행·디버깅 | 아래 폴더를 Gradle 프로젝트로 열기 |
| VS Code | React·TypeScript 프론트 편집·개발 서버 실행 | 아래 프론트 폴더 열기 |
| MySQL Server | 기업·공시·시세·수집 기록 저장 | 이미 설치됨. 다시 설치할 필요 없음 |
| MySQL Workbench | DB를 눈으로 조회하는 선택 도구 | 없어도 앱 실행 가능 |
| Java 21·Node·npm | 백엔드·프론트 실행 환경 | 이미 설치됨 |

Mock도 MySQL에 저장하므로 DB가 필요합니다. IntelliJ와 VS Code는 편집 도구이고 MySQL Server는 별도 실행 프로세스입니다. 이 안내는 설치된 MySQL을 사용하므로 Docker가 필요하지 않습니다.

## 1. IntelliJ에서 백엔드 열기 — 처음 한 번

IntelliJ의 **File → Open**으로 아래 폴더를 엽니다. 프론트 폴더나 outputs 전체 대신 백엔드 폴더를 선택합니다.

```text
C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend
```

Gradle 프로젝트로 가져오고 동기화를 기다립니다. 빌드 도구 선택이 나오면 Gradle을 선택합니다.

- **File → Project Structure → Project SDK**: Java 21
- **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**: Java 21
- Gradle 배포판은 프로젝트의 **Wrapper** 사용
- 현재 설치된 JDK 폴더: `C:\Program Files\Amazon Corretto\jdk21.0.12_8`

실행 설정은 **Run → Edit Configurations → + → Application**으로 만듭니다. Spring Boot 유형을 사용할 수 있으면 그것을 사용해도 됩니다.

| 항목 | 값 |
|---|---|
| Name | Stock Insight Real IDE |
| JDK | Java 21 |
| Main class | com.stockinsight.StockInsightApplication |
| Use classpath of module | 백엔드 main 소스가 포함된 모듈(보통 stock-insight-backend.main) |
| Working directory | 위 백엔드 폴더 |

**Environment variables**의 `.env` 파일 선택 기능으로 아래 기존 파일을 지정합니다. 항목이 숨겨져 있으면 **Modify options**에서 환경변수 설정을 표시합니다.

```text
C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend\.env.real
```

Spring Boot 자체는 `.env.real`을 자동으로 읽지 않습니다. **IDE가 이 파일을 환경변수로 로드하도록 설정하는 단계가 필요합니다.** 이 파일의 `SPRING_PROFILES_ACTIVE=real`, `APP_DATA_MODE=REAL`, 기존 DART·FSC 키를 사용합니다. 기존 파일을 덮어쓰거나 키를 다시 발급할 필요는 없습니다.

IDE 버전에 `.env` 파일 선택 기능이 없다면 **Edit environment variables** 표에 위 파일의 `SPRING_PROFILES_ACTIVE`, `APP_DATA_MODE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DART_API_KEY`, `FSC_API_KEY`를 각각 같은 값으로 등록합니다. 키 값은 PC의 IDE에만 입력합니다.

**Program arguments**에는 아래 내용을 그대로 넣습니다. 여러 줄을 지원하지 않는 입력칸이면 공백으로 이어 붙여 한 줄로 넣습니다. `${MYSQL_USER}`와 `${MYSQL_PASSWORD}`는 그대로 두면 Spring이 환경변수 값을 읽습니다.

```text
--server.port=8080
--spring.datasource.url=jdbc:mysql://127.0.0.1:13397/stock_insight_real?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC
--spring.datasource.username=${MYSQL_USER}
--spring.datasource.password=${MYSQL_PASSWORD}
```

위 명시적 인자는 기존 `.env.real`의 서버·DB 포트보다 우선합니다. 기존 일괄 실행용 설정 파일은 바꾸지 않습니다. DB 이름을 별도로 바꿨다면 JDBC URL의 `stock_insight_real`을 아래 DB 시작 명령의 READY에 표시되는 이름으로 맞춥니다. **Apply → OK**로 저장합니다.

설정 방법의 근거: [IntelliJ 환경변수·인자](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html), [Gradle JVM](https://www.jetbrains.com/help/idea/gradle-settings.html), [Spring 외부 설정·플레이스홀더](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

## 2. VS Code에서 프론트 열기 — 처음 한 번

VS Code의 **File → Open Folder**로 아래 폴더를 엽니다.

```text
C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend
```

**Terminal → New Terminal**로 PowerShell 터미널을 엽니다. 현재 PC에는 `node_modules`가 준비되어 있습니다. 폴더가 없어진 경우에만 프론트 폴더에서 `npm.cmd ci`로 의존성을 복원합니다.

현재 Vite 설정은 프론트5173에서 백엔드8080으로 `/api` 요청을 전달합니다. 위 백엔드 인자와 맞으므로 별도 프록시 수정이 필요 없습니다. 직접 만든 `.env.local`에 다른 `API_PROXY_TARGET`이 있다면 `http://127.0.0.1:8080`으로 맞추고 Vite를 재시작합니다.

터미널 사용법: [VS Code 공식 안내](https://code.visualstudio.com/docs/terminal/getting-started).

## 3. 매번 켜는 순서

### ① PowerShell에서 MySQL만 켜기

PowerShell 창 하나에서 실행합니다. IntelliJ의 PowerShell 터미널을 사용해도 됩니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-backend\scripts\start-ide-db.ps1"
```

`READY (DB ONLY)`가 나오면 창을 열어 둡니다. 이 명령은 설치된 MySQL을 IDE 전용 포트13397에서 시작하고, `.env.real`의 DB 이름·사용자·비밀번호로 DB를 준비합니다. 처음 실행할 때만 데이터 폴더를 초기화하고, 이후 저장 자료를 재사용합니다. **백엔드와 프론트는 이 명령이 켜지 않습니다.**

기존 Mock·Real·QA와 다른 데이터 폴더 `work/ide-real/mysql-data`를 사용합니다. 기존 Real 화면에서 수집한 데이터가 이 DB에 자동 복사되지는 않으므로 첫 기업 조회에는 수집 시간이 필요할 수 있습니다. DB 파일을 삭제해 재시작하지 않습니다.

### ② IntelliJ에서 백엔드 실행

위에서 저장한 **Stock Insight Real IDE**를 선택하고 **Run ▶**을 누릅니다. 중단점으로 확인하려면 **Debug**를 사용합니다. 콘솔에 `Started StockInsightApplication`과8080 포트의 시작 메시지가 나오면 준비된 것입니다. Flyway가 테이블을 자동으로 준비하므로 SQL 파일을 직접 실행하지 않습니다.

백엔드만 열었을 때8080 루트(`/`)에 화면이 없는 것은 정상입니다. 화면은 다음 프론트 서버에서 엽니다.

### ③ VS Code에서 프론트 실행

VS Code의 프론트 폴더 터미널에서 실행합니다.

```powershell
Set-Location 'C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend'
npm.cmd run dev
```

브라우저에서 [http://127.0.0.1:5173](http://127.0.0.1:5173/)을 열고 기아 또는000270을 검색합니다. 프론트 수정은 개발 서버가 반영하며, Java 코드 변경은 백엔드를 재실행하면 확실히 반영됩니다.

API를 직접 확인하려면 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)를 엽니다. Swagger 추가를 처음 반영할 때는 IntelliJ에서 Gradle 동기화 후 백엔드를 재시작합니다. 입력·실행·명세 저장은 [Swagger 안내](../stock-insight-backend/docs/swagger-guide.md)를 따릅니다.

| 구성 | 포트 | 시작 위치 |
|---|---|---|
| MySQL |13397 |DB 전용 PowerShell 명령 |
| Spring Boot 백엔드 |8080 |IntelliJ Run·Debug |
| React 프론트 |5173 |VS Code 터미널의 npm.cmd run dev |

이 개발 방법에서는 기존 `start-phase5-local.ps1`이나 `start-quality-check.ps1`을 함께 켤 필요가 없습니다. 그 명령들은 자체 백엔드·프론트까지 실행하는 별도의 일괄 실행 방법입니다. 이미 켜 둔 경우에는 해당 창의 안내에 따라 Enter로 종료하면 됩니다.

## 4. 끄기·다음 날 다시 켜기

1. VS Code의 프론트 터미널에서 **Ctrl+C**를 누릅니다.
2. IntelliJ의 백엔드를 **Stop ■**으로 종료합니다.
3. DB PowerShell 창에서 **Enter**를 눌러 MySQL을 종료합니다.

다음 날에는 **DB 명령 → IntelliJ Run → VS Code npm.cmd run dev** 순서만 반복합니다. IDE 설정·키 파일·DB 자료는 남으며 매번 설치·설정할 필요는 없습니다.

## 5. 오류 확인

| 현상 | 확인 |
|---|---|
| DB 연결 실패·Connection refused |DB READY 여부, JDBC 포트13397·DB 이름 |
| Access denied |IDE가 `.env.real`의 MYSQL_USER·MYSQL_PASSWORD를 환경변수로 로드했는지 확인 |
| `${MYSQL_USER}` 관련 오류 |Environment variables 설정 누락 여부 |
| DART 키·REAL 설정 오류 |같은 파일의 real·REAL·DART_API_KEY 로드 여부 |
| Java 버전·Gradle 동기화 오류 |Project SDK와 Gradle JVM 모두21인지 확인 |
| 프론트 API 연결 실패 |백엔드8080 시작 여부, API_PROXY_TARGET 설정 |
| 포트가 이미 사용 중 |같은 IDE 서버가 실행 중인지 확인하고 해당 서버를 먼저 정상 종료 |

DB 로그는 작업 폴더 `work/ide-real/mysql.stderr.log`에 있습니다. API 키나 `.env.real` 원문을 채팅·공개 Git에 올리지 않습니다. MySQL Workbench로 조회하려면 호스트127.0.0.1·포트13397·`.env.real`의 DB 사용자/비밀번호로 연결하면 됩니다.
