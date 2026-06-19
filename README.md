# AI Worktree Reviewer for Android Studio

Claude Code + Git Worktree 기반 멀티 세션 개발 환경에서 Android Studio 하나만 사용하여 여러 Worktree의 변경사항을 빠르게 탐색, 검토, 삭제할 수 있는 초경량 코드 리뷰 플러그인입니다.

AI 에이전트를 모니터링하거나 복잡한 메타데이터 결합 대신 **"Git Worktree 자체가 상태다"**라는 설계 철학을 바탕으로 개발되었습니다.

---

## 🎨 시뮬레이터 라이브 프리뷰
본 레포지토리는 웹 환경에서 이 플러그인의 레이아웃, 기능 정의, 그리고 Kotlin 실제 플러그인 구현 소스코드를 다운로드하고 시뮬레이션해볼 수 있는 고해상도 웹 어플리케이션을 제공합니다.

*   **웹 프리뷰 접속**: [공유 프리뷰 링크](https://ais-pre-t4wn3ktigmqmom7c6teoos-599136130733.asia-northeast1.run.app)
*   **주요 제공 요소**:
    *   **Side-by-Side Diff Viewer**: Android Studio 고유의 다크 테마(Darcula)와 화이트(Light) 테마를 그대로 옮겨온 비교 편집기
    *   **Interactive Terminal / Sandbox CLI**: 실제 CLI 환경과 연동되는 가상 쉘 환경 (`git worktree add`, `git worktree remove`, `git worktree list` 시뮬레이션 가능)
    *   **Plugin Kotlin Code Exporter**: 프로젝트 생성 즉시 그대로 복사 붙여넣기하여 빌드 구동할 수 있는 템플릿 코드 소스 정리

---

## 🚀 주요 기능 (MVP Core)

### 1. Active Worktrees 패널 (Tool Window)
*   Android Studio 우측 도킹 사이드바 및 Tool Window로 자동 등록됩니다.
*   현재 로컬 Git Repository에 배치된 모든 Active Worktree 목록을 자동으로 탐색하여 보여줍니다.
*   **데이터 수집 명령어**: `git worktree list --porcelain` 을 백그라운드 프로세스 스트림으로 주기적으로 읽어옵니다.
*   **설정 가능한 Polling**: Off (수동), 5초, 10초 간격 자동 동기화를 지원합니다.
*   **단축키 지원**: `Alt + Shift + W` (사용자가 IntelliJ Keymap에서 변경 가능)로 사이드바를 즉시 부팅/전환 가능합니다.

### 2. Worktree 정보 요약 및 변경 파일 명세
*   Worktree를 선택하면 하단 상태 도크 및 요약 보기에 타겟 데이터가 즉시 연동됩니다.
    *   활성화된 Sub-branch 명세 (`git -C <path> branch --show-current`)
    *   수정된 전체 파일 이름 목록 (`git -C <path> diff --name-only`)
    *   수정본 추가(+) 및 삭제(-) 라인 스펙 메커니즘 (`git -C <path> diff --shortstat`)

### 3. One-Click Side-by-Side Diff Review & Traverser
*   파일 목록에서 원하는 항목을 클릭하면 Android Studio의 **네이티브 Diff Viewer** 윈도우 탭이 즉시 열립니다.
*   **Traversal (Next / Previous) 단축 바 지원**: GitHub Pull Request 환경처럼 `Alt + Left / Right` 화살표 단축키 입력을 사용해 여러 수정 파일의 전체 컨텍스트를 빠르고 쾌적하게 횡단 탐색할 수 있습니다.

### 4. Open & Clean Removal
*   **Remove Worktree**: 작업을 성공적으로 검토하고 커밋, 머지한 후 화면 내 원터치 삭제를 누르면 백그라운드에서 `git worktree remove <path>`가 실행되어 불필요하게 남아있는 워크트리 디렉토리 메모리를 완벽하게 정리 및 인스턴스 해제합니다.
*   **Open Worktree**: 특수 예외 파일 확인을 위해 선택한 Worktree 디렉토리를 완전히 새로운 IDE 창 프레임으로 가볍게 분리 실행 시킬 수 있는 외부 위젯 버튼을 별도로 제공합니다.

---

## 🛠️ 설계 원칙 (Design Principles)

1.  **상태를 외부 파일에 저장하지 않는다**: `.ai-review.json` 등 어떠한 비표준 마커 세팅이나 UI 상태 변수(reviewed, approved 등)는 사용하지 않습니다. 오직 Git Worktree의 물리적 존립 상태만을 타겟 상태로 존중합니다.
2.  **Git Worktree 자체가 완벽한 상태이다**: Worktree 폴더가 있으면 리뷰할 대상이고, 해당 폴더가 사라지면 해당 작업 트랙은 완벽히 종료된 것으로 간주합니다.
3.  **Claude 토큰 낭비 제거**: 원격 API로 Diff를 번역하거나 AI에게 다시 질의하여 피드백받기 위한 추가적인 AI 연계를 배제하고, 로컬 하드웨어 쉘 프로세스의 속도로 실행되어 최적의 속도를 유지합니다.

---

## 📂 IntelliJ 플러그인 실제 구현 파일 스펙 (Sources Map)

공식 플러그인을 컴파일하고 local 디렉토리에서 곧바로 띄워보기 위한 핵심 코드 구조는 웹 에뮬레이터 내부의 **[Plugin Kotlin Code]** 탭에서 제공하고 있습니다.

### 1. `plugin.xml`
> 위치: `/src/main/resources/META-INF/plugin.xml`
*   플러그인 고유 ID 연동 및 Android Studio용 사이드 도구창(`Active Worktrees` Tool Window) 레이아웃 선언부.
*   `Alt + Shift + W` 글로벌 단축키 바인딩 및 Menu bar UI 배치 명세.

### 2. `build.gradle.kts`
> 위치: `/build.gradle.kts`
*   IntelliJ Platform SDK Gradle 플러그인 및 타겟 빌드 컴파일 타깃 지정 (Kotlin jvmTarget = "17" 호환).
*   Git 통합을 위한 `git4idea` 모듈 의존성 결합 설정.

### 3. `WorktreeService.kt`
> 위치: `/src/main/kotlin/com/example/worktree/service/WorktreeService.kt`
*   로컬 JVM 내부 `ProcessBuilder` 인스턴스를 통해 `git worktree list --porcelain`, `git diff` 명령어 출력 결과를 읽어오고 파싱하여 Swing 컨포넌트 모델에 안전하게 매핑해주는 핵심 서비스 레이어.

### 4. `ActiveWorktreesToolWindowFactory.kt`
> 위치: `/src/main/kotlin/com/example/worktree/ui/ActiveWorktreesToolWindowFactory.kt`
*   `ToolWindowFactory` 주입 인터페이스로, JBList 렌더러와 스크롤 패널, Refresh 버튼 클릭 등의 이벤트를 핸들링하는 비주얼 가이드 뷰어.

### 5. `DiffReviewAction.kt`
> 위치: `/src/main/kotlin/com/example/worktree/actions/DiffReviewAction.kt`
*   특정 파일 클릭 시, 가상 LocalFileSystem을 사용하고 `DiffManager.getInstance().showDiff`를 호출하여 Android Studio 네이티브 Side-by-Side Diff 편집기 창에 원본 코드와 수정 코드를 안전하게 띄워주는 액션 핸들러.

---

## 👾 로컬 빌드 및 안드로이드 스튜디오(Android Studio) 설치 방법

제시된 코드는 플러그인의 **전체 소스코드**입니다. 실제 동작하는 플러그인 형태로 안드로이드 스튜디오에 직접 설치하려면, 소스코드를 컴파일하여 **플러그인 배포용 압축 파일(`.zip` 또는 `.jar`)**을 생성해야 합니다.

### 📦 1단계: 플러그인 설치 파일(.zip) 빌드하기
개발 장비에 IntelliJ/Android Studio 개발 환경과 JDK 17 버전이 설정되어 있다면 바로 빌드할 수 있습니다.

1.  IntelliJ IDEA를 실행한 후 **[New Project] → [IDE Plugin]**으로 신규 프로젝트를 생성합니다. (Kotlin DSL Gradle 프로젝트 설정 권장)
2.  본 에뮬레이터의 `Plugin Kotlin Code` 탭 소스코드를 프로젝트의 지정된 경로에 각각 붙여넣습니다.
    *   `build.gradle.kts`는 루트 디렉토리에 덮어씌웁니다.
    *   `plugin.xml`은 `src/main/resources/META-INF/` 하위에 위치시킵니다.
    *   Kotlin 파일들은 `src/main/kotlin/com/example/worktree/` 패키지 하위에 구성합니다.
3.  프로젝트 루트 터미널에서 다음 Gradle 명령어(플러그인 패키징 공식 명령)를 실행합니다:
    ```bash
    ./gradlew buildPlugin
    ```
4.  빌드가 완료되면 **`build/distributions/`** 디렉토리 하위에 **`AI-Worktree-Reviewer-1.0.0.zip`** 형태의 플러그인 최종 배포 파일이 생성됩니다. 이 파일이 안드로이드 스튜디오에 직접 업로드하여 바로 쓸 수 있는 **플러그인 설치용 파일**입니다!

---

### 💻 2단계: 안드로이드 스튜디오에 직접 설치하기
빌드된 `.zip` 파일을 사용해 안드로이드 스튜디오에 수동 설치 방법은 다음과 같습니다:

1.  안드로이드 스튜디오(Android Studio)를 엽니다.
2.  상단 메뉴에서 **Settings** (Windows/Linux: `File` -> `Settings` 또는 `Ctrl+Alt+S` / macOS: `Android Studio` -> `Settings...` 또는 `Cmd+,`)를 엽니다.
3.  좌측 메뉴에서 **Plugins**를 클릭합니다.
4.  우측 상단의 **톱니바퀴 아이콘(⚙️)**을 클릭한 뒤, **"Install Plugin from Disk..."**를 선택합니다.
5.  앞선 단계에서 빌드하여 생성된 `build/distributions/AI-Worktree-Reviewer-1.0.0.zip` 파일을 찾아 선택합니다.
6.  **Apply** 버튼을 누르고 안드로이드 스튜디오를 **재시작(Restart IDE)** 해줍니다.
7.  이제 키맵 단축키 `Alt + Shift + W`를 입력하면 Android Studio 내부에 강력하고 가벼운 **Active Worktrees** 도구가 활성화됩니다!

---

## 🛠️ 개발 팁: 안드로이드 스튜디오(Android Studio) 타겟 개발 상세
안드로이드 스튜디오는 기본적으로 JetBrains의 IntelliJ Community Platform을 기반으로 작동하므로 완벽하게 호환됩니다.

*   **컴파일 SDK 매핑**: 안드로이드 스튜디오 버전과 매칭되는 IntelliJ Platform 버전을 `build.gradle.kts`에 작성합니다.
    *   예: **Android Studio Ladybug (2024.2.1)** 은 IntelliJ `2024.2` 기반이므로 `version.set("2024.2")` 로 작성합니다.
    *   예: **Android Studio Koala (2024.1.1)** 는 IntelliJ `2024.1` 기반이므로 `version.set("2024.1")` 로 작성합니다.
*   **로컬 디버그 환경**: 로컬에 설치된 Android Studio를 디렉토리로 지정하여 직접 애뮬레이션 실행 및 테스트하고 싶다면 `build.gradle.kts` 의 `intellij { ... }` 블록 내부에 다음 설정을 추가해주면 됩니다.
    ```kotlin
    intellij {
        // 본인의 OS에 맞게 설치된 Android Studio 경로 입력
        localPath.set("/Applications/Android Studio.app") // macOS 예시
        // localPath.set("C:\\Program Files\\Android\\Android Studio") // Windows 예시
    }
    ```

---

## 🤖 GitHub Actions로 자동 빌드 및 배포하기 (가장 편리한 방법 🌟)

가장 권장하는 방식은 **GitHub Actions**를 사용하여 GitHub 클라우드에서 플러그인 파일을 자동으로 컴파일하고 배포하는 것입니다. 이렇게 하면 개발 PC에 Java나 Gradle, IntelliJ 등을 복잡하게 설치하지 않고도 항상 최신 플러그인 ZIP 파일을 얻을 수 있습니다.

이미 이 레포지토리의 `.github/workflows/build-plugin.yml`에 자동 빌드 구성이 완벽히 세팅되어 제공됩니다!

### ⚙️ 동작 방식 & 다운로드하는 법

1. **자동 빌드 (Push / Pull Request)**
   * 코드를 업데이트하여 GitHub의 `main` 혹은 `master` 브랜치로 `git push`를 하면, GitHub Actions가 백그라운드에서 자동으로 빌드를 수행합니다.
   * **다운로드**: GitHub Repository 내의 **[Actions]** 탭 -> 최신 빌드 Workflow 클릭 -> 하단 **Artifacts** 섹션에서 빌드된 `AI-Worktree-Reviewer-Artifact` ZIP 파일을 다운로드할 수 있습니다!

2. **작업물 공식 배포 (GitHub Release - 강력 추천)**
   * GitHub에서 새로운 **Release**(태그 생성)를 발행하면 자동 빌드가 동작합니다.
   * 빌드가 즉시 완료된 후, 자동으로 **해당 Release의 첨부 파일(Release Assets) 항목**에 완벽히 호환되는 최신 플러그인 빌드 ZIP 파일이 업로드됩니다.
   * 사용자는 웹 브라우저로 Releases 페이지에 와서 `.zip` 파일만 다운받아 안드로이드 스튜디오에 'Install Plugin from Disk'로 넣기만 하면 됩니다.

