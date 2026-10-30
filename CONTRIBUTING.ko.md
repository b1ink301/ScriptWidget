# 기여 가이드

[English](CONTRIBUTING.md) | **한국어**

ScriptWidget에 기여해 주셔서 고맙습니다. 가장 쉬운 기여는 **갤러리 샘플 추가**입니다. 코드 기여도 환영합니다.

## 갤러리에 샘플 추가하기

기본 갤러리는 이 저장소의 [`data/src/main/resources/samples/catalog.json`](data/src/main/resources/samples/catalog.json)을 읽습니다. `main`에 들어간 샘플은 **앱을 다시 배포하지 않아도** 모든 사용자의 갤러리에 나타납니다.

1. `data/src/main/resources/samples/`에 스크립트(`my_widget.js`)를 추가합니다.
2. `catalog.json`의 `entries`에 항목을 추가합니다.

```json
{
  "id": "my_widget",
  "name": "내 위젯",
  "title": "내 위젯",
  "category": "정보",
  "description": "무엇을 보여 주는지 한두 문장으로.",
  "script": "my_widget.js",
  "version": 1,
  "sizes": ["small", "medium"],
  "permissions": ["network"],
  "author": "GitHub 아이디"
}
```

| 필드 | 규칙 |
|---|---|
| `id` | 영문 소문자·숫자·`_`. 다른 항목과 겹치면 안 됨 |
| `name` | 설치될 스크립트 이름. `/`·`\` 없이, 64자 이하 |
| `category` | `일정·할 일` · `생활` · `정보` · `예제` · `디자인` 중 하나 |
| `sizes` | `small` · `medium` · `large` 중 지원하는 크기 (1개 이상) |
| `permissions` | 스크립트가 쓰는 것: `calendar` `contacts` `location` `network` `notification` `photos` |
| `version` | 이미 있는 샘플을 고치면 **1 올립니다** → 설치한 사용자에게 "업데이트"가 보입니다 |

### 샘플 기준
- **API 키 없이** 동작해야 합니다. 키가 필요하면 위젯 파라미터나 `Keychain`으로 받게 하세요.
- 선언한 모든 크기에서 에러 없이 그려야 합니다. `config.widgetFamily`로 크기별 배치를 나누세요.
- 데이터를 못 받으면 위젯이 깨지지 않고 안내 문구를 보여 주세요.
- 다른 사람이 만든 스크립트는 **원작자의 허락과 라이선스**를 확인한 뒤에 올려 주세요.

### 디자이너로 만든 위젯 올리기
디자이너 **⋮ → 갤러리용으로 내보내기**로 저장한 zip에는 스크립트와 `entry.json`(카탈로그 항목, 권한 자동 계산)이 들어 있습니다. 스크립트를 `samples/`에 넣고 `entry.json`을 `catalog.json`의 `entries`에 붙인 뒤, 제목·설명·분류를 다듬으면 됩니다.

### 확인하기
```bash
./gradlew :data:test --tests '*BuiltinCatalogValidationTest' --tests '*GallerySamplesTest'
```
- 카탈로그 검사: 항목 필드, 그리고 **코드가 쓰는 API와 `permissions`가 맞는지**(예: `Location.` → `location`, `Request(` → `network`). PR에서도 CI가 자동으로 검사합니다.
- 샘플 실행: 새 샘플은 실제 QuickJS 엔진에서 모든 크기로 실행됩니다.

## 나만의 카탈로그 공유하기

갤러리 화면의 **+ 카탈로그**에 https 주소의 `index.json`을 넣으면 다른 사람의 카탈로그를 볼 수 있습니다. 형식은 `catalog.json`과 같고, 스크립트 경로는 `index.json` 기준 상대 경로입니다. 공유하기 전에 같은 검사로 확인할 수 있습니다.

```bash
./gradlew :data:test --tests '*CatalogFileCheck*' -Pcatalog=/path/to/index.json
```

앱은 원격 스크립트를 설치하기 전에 코드와 권한을 보여 줍니다. 카탈로그에 적지 않은 권한을 코드가 쓰면 경고를 띄웁니다.

## 코드 기여

- 브랜치: `develop`에서 작업 브랜치를 만들어 `develop`으로 PR을 보냅니다.
- 구조: Clean Architecture(`domain` → `data` / `data-android` → `widget` → `app`). 자세한 내용은 [README](README.ko.md#아키텍처-clean-architecture)를 보세요.
- **TDD**: 테스트를 먼저 쓰고(실패 확인) 구현합니다. 단위 테스트는 `./gradlew test`, 기기 테스트는 에뮬레이터에서 `./gradlew connectedDebugAndroidTest`로 돌립니다.
- 스타일: `./gradlew spotlessApply`로 정리하고 `./gradlew spotlessCheck lintDebug`가 통과해야 합니다.
- 커밋 메시지: `feat(gallery): ...`, `fix(widget): ...`처럼 범위를 붙입니다.

## 라이선스
기여한 코드와 샘플은 이 저장소의 [GPL-3.0](LICENSE) 라이선스로 배포됩니다.
