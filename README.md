# 교사 시간표 안드로이드 위젯

보성여고 선택과목 대시보드의 시트에서 오늘 시간표를 세로로 표시하는 홈 위젯.

## 빠르게 APK 빌드받기 (PC에 아무것도 설치 안 함)

### 1. GitHub에 이 프로젝트 올리기

**웹브라우저만으로:**

1. github.com 로그인
2. 우측 상단 `+` → `New repository`
3. Repository name: `teacher-widget` (원하는 이름)
4. `Public` 또는 `Private` 선택
5. `Create repository` 클릭 (README 체크는 해제)
6. 만들어진 저장소 페이지에서 `uploading an existing file` 링크 클릭
7. 이 폴더의 **모든 파일을 드래그 앤 드롭** (폴더째 끌어다 놓으면 구조 그대로 올라감)
8. 하단 `Commit changes` 클릭

### 2. 자동 빌드 기다리기

- 업로드 완료 후 저장소 상단 **Actions** 탭 클릭
- `Build APK` 워크플로가 자동 실행됨 (3~5분 걸림)
- 초록색 체크 뜨면 해당 실행 이름 클릭
- 페이지 하단 `Artifacts` 섹션에서 `teacher-widget-debug-apk` 다운로드 (zip)

### 3. 폰에 설치

1. 다운받은 zip 압축 풀면 `app-debug.apk` 나옴
2. 폰으로 전송 (파일 공유, 이메일, 카톡 나에게 보내기 등)
3. 폰에서 탭하면 설치. "알 수 없는 앱 설치" 허용 필요할 수 있음
4. 홈 화면 길게 누름 → 위젯 → `교사시간표위젯` 선택
5. 설치 시 교사 드롭다운에서 본인 선택 → 확인

## 폴더 구조

```
teacher-widget/
├── .github/workflows/build.yml     GitHub Actions 빌드 설정
├── app/
│   ├── build.gradle                앱 모듈 설정
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/my/time/       Kotlin 코드 3개
│       └── res/                    레이아웃, 리소스
├── build.gradle                    루트 설정
├── settings.gradle
├── gradle.properties
└── .gitignore
```

## 주요 기능

- Config Activity에서 교사 드롭다운 선택 → 설정 저장
- 오늘 요일 기준 1~10교시 중 실제 수업 있는 교시만 표시
- 위젯 헤더 탭하면 수동 새로고침
- 30분마다 자동 업데이트 (시스템 정책 최솟값)
- 주말엔 월요일 시간표로 폴백

## 커스터마이징

- `DataFetcher.kt`의 `SHEET_ID`: 시트 변경 시 수정
- `widget_info.xml`의 `updatePeriodMillis`: 자동 갱신 주기
- `widget_layout.xml`: 위젯 모양 수정


## 이번 수정 사항

- 3x2 기본 크기로 조정
- 최대 7교시까지만 표시
- 위젯 상단의 선생님 이름 탭 시 설정창 진입
- 헤더 탭 시 수동 새로고침
- Kotlin 빌드 오류(`ifEmpty { continue }`) 수정


## 추가 수정 사항 (반응형 위젯)

- 위젯 크기 변경 시 `onAppWidgetOptionsChanged()`로 다시 렌더링
- 높이/너비에 따라 글자 크기, 패딩, 교시 폭 자동 조절
- 좁은 크기에서는 반 정보 숨김으로 7교시 우선 표시
