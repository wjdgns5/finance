# GitHub 저장소와 로컬 프로젝트

저장소: [wjdgns5/finance](https://github.com/wjdgns5/finance)

기존 파일·실행 스크립트 경로를 유지하는 단일 저장소입니다. 저장소 루트에 시작 README·Git 제외 규칙을 두고, 실제 소스와 설계·검증 문서는 `outputs` 아래에 둡니다.

## 들어 있는 파일

- `outputs/stock-insight-backend`: Java 소스·Gradle Wrapper·Flyway·테스트·Swagger·API/DB 문서.
- `outputs/stock-insight-frontend`: React·TypeScript 소스·잠금 파일·실행 스크립트·검증 화면.
- `outputs/docs`: SDD·설계 결정·검증·IntelliJ/VS Code 안내.
- 키 값이 비어 있는 `.env.example`, `.env.real.example`.

## 로컬에만 남는 파일

실제 `.env`·`.env.real`, `work`의 MySQL 파일·수집 캐시·로그, `node_modules`, `build`, `dist`, `.gradle`, IDE 개인 설정은 제외합니다. Gradle Wrapper JAR는 빌드 재현에 필요한 파일이므로 포함합니다.

GitHub에서 다시 받으면 키·저장 DB·빌드 결과가 함께 복원되지는 않습니다. 예시 환경설정에서 새 로컬 파일을 만들고 의존성·빌드를 준비합니다. 기존 PC의 키 파일과 DB를 덮어쓰거나 삭제할 필요는 없습니다.

## 다시 받아서 열기

원하는 개발 폴더의 PowerShell에서 실행합니다.

```powershell
git clone https://github.com/wjdgns5/finance.git
Set-Location '.\finance'
```

IntelliJ에서는 `outputs/stock-insight-backend`, VS Code에서는 `outputs/stock-insight-frontend`를 엽니다. Java21·Node24 이상·MySQL을 준비하고 [상세 시작 문서](../README.md)·[IDE 안내](ide-development-guide.md)를 따릅니다. 기존 문서의 `C:\Users\rhfov\...` 경로는 원래 PC 기준이므로 새 clone 위치로 바꿉니다.

## 이후 변경 올리기

변경 내용을 확인하고 소스·문서·예시 설정만 포함되는지 검토한 뒤 커밋합니다.

```powershell
git status --short
git diff
git add README.md .gitignore .gitattributes outputs
git diff --cached --stat
git commit -m "Describe the change"
git push origin main
```

실제 API 키가 들어 있는 파일을 강제로 추가하지 않습니다. 공개 저장소에 키를 포함하지 않는 것은 `.gitignore`와 업로드 전 검사로 확인합니다. 로컬 실행·종료는 기존 [PowerShell 안내](../stock-insight-frontend/docs/local-run-guide.md)를 사용합니다.
