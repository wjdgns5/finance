# PowerShell 서버 시작·종료·재시작

현재 Windows PC·Stock Insight0.6.0 기준입니다. 평소 실행·종료·재부팅 후 재실행 절차입니다. 처음 의존성과 빌드 파일을 준비할 때는 [프로젝트 README](../../README.md)를 먼저 확인합니다.

## 1. 필요한 서버만 선택

**실제 주식을 조회할 때는 아래 ② Real 하나만 실행하면 됩니다.** ① Mock과 ③ QA는 확인·개발할 때 필요합니다.

| 번호 | 용도 | 필요한 창 | 브라우저 주소 |
|---|---|---|---|
| ① Mock | 가상 기업·손실·우선주·결측 사례 | PowerShell1개 | [가상 화면](http://127.0.0.1:5194/) |
| ② Real | DART·금융위원회 실제 기업 자료 | PowerShell1개 | [실제 화면](http://127.0.0.1:5195/) |
| ③ QA | 프로덕션 빌드·오류·복구 검증 | PowerShell1개 | [검증 제어](http://127.0.0.1:5206/__quality) |

①과②를 함께 쓰면2개 창, ①②③을 모두 켜면3개 창입니다. PowerShell 창 대신 Windows Terminal의 별도 PowerShell 탭을 써도 됩니다. **명령 하나당 창 또는 탭 하나**를 사용합니다.

세 명령은 서로 독립적입니다. ①→②→③ 순서로 실행해도 되고, ②만 실행해도 됩니다. ③도 ①②를 먼저 켤 필요가 없습니다.

## 2. 서버 켜기

1. Windows에서 PowerShell을 엽니다. 현재 설치된 실행 환경에서는 관리자 권한·Docker 실행이 필요하지 않습니다.
2. 사용할 모드의 명령 하나를 붙여 넣고 Enter를 누릅니다. 어느 폴더에서 실행해도 됩니다.
3. `READY` 또는 `READY (REAL)`가 나올 때까지 기다립니다. 첫 DB 초기화는 조금 더 걸릴 수 있습니다.
4. 해당 브라우저 주소를 엽니다. **서버를 사용하는 동안 이 PowerShell 창을 열어 둡니다.** READY 뒤 종료 대기 상태에서 Enter를 누르면 서버가 종료됩니다.

### ① Mock — 가상 사례

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-phase5-local.ps1" -Mode Mock
```

화면: [http://127.0.0.1:5194](http://127.0.0.1:5194/)

한빛제조 또는990001을 선택합니다. API 키가 필요하지 않습니다. 표시되는 기업·수치는 가상입니다.

### ② Real — 실제 기업 조회

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-phase5-local.ps1" -Mode Real
```

화면: [http://127.0.0.1:5195](http://127.0.0.1:5195/)

기아 또는000270을 검색해 선택합니다. 처음 조회하는 기업은 공시 수집 때문에 시간이 걸릴 수 있습니다.

키 설정 파일은 백엔드 폴더의 `.env.real`입니다. 이미 저장한 설정은 PC를 껐다 켜도 파일에 남습니다. 정상 설정을 매번 다시 입력할 필요는 없습니다. 시세까지 조회하려면 FSC 키도 필요합니다. 실제 키를 채팅·문서에 복사하지 않습니다.

`start-phase5-local.ps1`은 Phase5에서 만든 파일 이름을 그대로 사용합니다. 현재는 내부 실행 파일이 **백엔드0.6.0 JAR**을 사용하므로 이 명령을 계속 쓰면 됩니다.

### ③ QA — 품질·오류 처리 검증

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend\scripts\start-quality-check.ps1"
```

화면: [http://127.0.0.1:5206/__quality](http://127.0.0.1:5206/__quality)

검증 시나리오를 선택하고 `적용하고 앱 열기`를 누릅니다. 정상 확인은 `정상 통합 흐름`을 선택합니다. 다른 항목은 연결 끊김·부분 실패 등을 의도적으로 재현합니다. **평소 실제 기업 조회에는 이 서버가 필요하지 않습니다.** API 키 없이 전용 Mock DB로 동작합니다.

QA는 프론트엔드의 `dist` 빌드 결과를 사용합니다. 코드 변경 없이 다음 날 다시 켜는 경우 빌드를 다시 할 필요가 없습니다. 프론트엔드 코드를 바꾼 뒤 QA 화면에 반영하려면 먼저 다음 명령으로 빌드합니다.

```powershell
Set-Location 'C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1\outputs\stock-insight-frontend'
npm.cmd run build
```

## 3. 내부 시작 순서와 창 수

각 명령이 아래 작업을 자동으로 처리하므로 MySQL·Spring Boot·프론트엔드를 따로 실행하지 않습니다.

1. 해당 모드의 전용 MySQL DB를 시작합니다.
2. 백엔드를 시작하고 API 준비 상태를 확인합니다.
3. 화면 서버를 시작하고 API 연결을 확인합니다.
4. READY와 접속 주소를 표시합니다.

| 모드 | 화면 포트 | 백엔드 포트 | MySQL 포트 | 보존되는 DB 폴더 |
|---|---|---|---|---|
| Mock | 5194 | 18094 | 13394 | `work/phase5-browser-mock/mysql-data` |
| Real | 5195 | 18095 | 13395 | `work/phase5-browser-real/mysql-data` |
| QA | 5206 | 18096 | 13396 | `work/phase6-browser/mysql-data` |

위 DB 폴더는 `C:\Users\rhfov\Documents\Codex\2026-11-17\phase-0-phase-1` 아래에 있습니다. 서로 다른 폴더·포트를 사용하므로 세 모드를 동시에 켤 수 있습니다.

현재 PC에는 필요한 MySQL·Java21·Node가 설치되어 있고0.6.0 JAR·프론트엔드 의존성·dist가 준비되어 있습니다. 일반적인 종료·재시작 때는 재설치나 `npm ci`가 필요하지 않습니다.

## 4. 서버 끄기

1. 서버를 실행해 둔 PowerShell 창으로 돌아갑니다.
2. READY 뒤 종료 대기 안내가 나온 상태에서 **Enter**를 누릅니다.
3. 명령이 끝나고 `PS ...>` 프롬프트가 다시 나오는지 확인합니다.
4. 여러 모드를 켰다면 각각의 창에서 반복합니다. 종료 순서는 상관없습니다.

각 실행 파일은 자신이 시작한 화면·백엔드·DB 프로세스만 종료합니다. 브라우저 창만 닫으면 서버는 계속 실행됩니다. 정상 종료는 PowerShell 창을 강제로 닫는 대신 위 Enter 절차를 사용합니다.

DB 폴더와 `.env.real` 설정 파일은 종료해도 보존합니다. 같은 경로로 다시 실행하면 저장 자료를 재사용합니다. 저장 자료 재사용이 최신 거래일·최신 정정 공시 갱신을 의미하지는 않습니다.

## 5. 다음 날 또는 PC 재부팅 후 다시 켜기

1. 필요한 모드의 PowerShell 창을 엽니다.
2. 위와 **같은 실행 명령**을 다시 붙여 넣습니다. 실제 기업 조회만 필요하면② 하나만 실행합니다.
3. READY가 나오면 같은 브라우저 주소를 엽니다.

이미 실행 중인 서버를 재시작하려면 먼저 해당 창에서 Enter로 종료하고, 명령이 끝난 뒤 같은 명령을 다시 실행합니다. 같은 모드를 중복 실행하지 않습니다. 백엔드 코드나 키 설정을 바꾼 경우에도 서버를 다시 시작해야 반영됩니다.

브라우저 접속 시 서버가 꺼져 있으면 연결할 수 없다는 화면이 나옵니다. 먼저 해당 PowerShell 서버를 켠 뒤 새로고침합니다.

## 6. 실행 오류가 날 때

| 오류·상황 | 확인할 것 |
|---|---|
| `이미 사용 중인 포트`, `Port is already in use` | 같은 모드가 다른 창에서 실행 중인지 확인하고 그 창에서 Enter로 종료한 뒤 다시 실행 |
| `ibdata1 must be writable`, `MySQL exited` | 이전 MySQL이 같은 DB 파일을 사용 중이거나 쓰기 권한이 없는지 확인. 이전 실행을 정상 종료하고 해당 모드의 로그 확인 |
| 필요한 JAR이 없다는 오류 | 현재 백엔드 빌드 파일 `stock-insight-backend-0.6.0.jar`가 있는지 확인 |
| QA 실행 시 빌드 파일 없음 | 프론트엔드 폴더에서 `npm.cmd run build` 실행 |
| REAL 키 설정 오류 | 백엔드 `.env.real` 설정을 확인하고 REAL 서버 재시작 |
| 브라우저 연결 실패 | READY 여부·창이 열려 있는지·모드별 주소가 맞는지 확인 |

로그는 해당 모드의 `work/phase5-browser-mock`, `work/phase5-browser-real`, `work/phase6-browser`에 있습니다. `backend.stdout.log`, `frontend.stderr.log`, `mysql.stderr.log`에서 실패 지점을 확인할 수 있습니다. 포트·DB 오류를 해결하려고 DB 폴더를 삭제하지 않습니다.

상세 검증·제한사항은 [Phase6 보고서](phase-6-verification.md), 프로젝트 기능은 [프론트엔드 README](../README.md)를 참조하세요.
