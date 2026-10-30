# Contributing

**English** | [한국어](CONTRIBUTING.ko.md)

Thank you for contributing to ScriptWidget. The easiest contribution is **adding a gallery sample**. Code contributions are welcome too.

## Adding a sample to the gallery

The built-in gallery reads [`data/src/main/resources/samples/catalog.json`](data/src/main/resources/samples/catalog.json) in this repository. Samples merged into `main` appear in every user's gallery **without a new app release**.

1. Add a script (`my_widget.js`) to `data/src/main/resources/samples/`.
2. Add an entry to `entries` in `catalog.json`.

```json
{
  "id": "my_widget",
  "name": "My widget",
  "title": "My widget",
  "category": "정보",
  "description": "One or two sentences on what it shows.",
  "script": "my_widget.js",
  "version": 1,
  "sizes": ["small", "medium"],
  "permissions": ["network"],
  "author": "GitHub username"
}
```

`category` must be one of the existing categories, written in Korean as in `catalog.json` (here `정보` = Info). Put English text in `translations.en` (`title`, `description`, `category`).

| Field | Rules |
|---|---|
| `id` | Lowercase letters, digits and `_`. Must not collide with other entries |
| `name` | Name of the installed script. No `/` or `\`, 64 characters or fewer |
| `category` | One of the existing categories: `일정·할 일` (Calendar & To-dos) · `생활` (Everyday) · `정보` (Info) · `예제` (Examples) · `디자인` (Designs) |
| `sizes` | Supported sizes among `small` · `medium` · `large` (at least one) |
| `permissions` | What the script uses: `calendar` `contacts` `location` `network` `notification` `photos` |
| `version` | When you change an existing sample, **bump it by 1** → users who installed it see "Update" |

### Sample guidelines
- It must work **without an API key**. If a key is needed, take it via the widget parameter or `Keychain`.
- It must draw without errors in every declared size. Use `config.widgetFamily` to vary the layout per size.
- If data can't be loaded, show a message instead of a broken widget.
- For scripts written by someone else, check **the original author's permission and license** before submitting.

### Submitting a widget made with the designer
The zip saved with the designer's **⋮ → Export for a gallery** contains the script and `entry.json` (the catalog entry, with permissions computed automatically). Put the script in `samples/`, add `entry.json` to `entries` in `catalog.json`, then polish the title, description and category.

### Checking
```bash
./gradlew :data:test --tests '*BuiltinCatalogValidationTest' --tests '*GallerySamplesTest'
```
- Catalog validation: entry fields, and **whether the APIs the code uses match `permissions`** (e.g. `Location.` → `location`, `Request(` → `network`). CI also runs this automatically on PRs.
- Sample run: new samples are run in every size on the real QuickJS engine.

## Sharing your own catalog

Putting an `index.json` at an https address into **+ Catalog** in the Gallery screen lets you browse someone else's catalog. The format is the same as `catalog.json`, and script paths are relative to `index.json`. You can check it with the same validation before sharing.

```bash
./gradlew :data:test --tests '*CatalogFileCheck*' -Pcatalog=/path/to/index.json
```

Before installing a remote script, the app shows its code and permissions. If the code uses permissions not listed in the catalog, it shows a warning.

## Code contributions

- Branches: create a working branch from `develop` and send the PR to `develop`.
- Structure: Clean Architecture (`domain` → `data` / `data-android` → `widget` → `app`). See the [README](README.md#architecture-clean-architecture) for details.
- **TDD**: write the test first (confirm it fails), then implement. Run unit tests with `./gradlew test` and device tests on an emulator with `./gradlew connectedDebugAndroidTest`.
- Style: format with `./gradlew spotlessApply`; `./gradlew spotlessCheck lintDebug` must pass.
- Commit messages: add a scope, like `feat(gallery): ...` or `fix(widget): ...`.

## License
Contributed code and samples are distributed under this repository's [GPL-3.0](LICENSE) license.
