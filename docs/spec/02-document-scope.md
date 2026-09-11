# 2. この仕様書の責務

この仕様書一式はユーザーから見た現行機能と、互換性に影響する主要な振る舞いを説明する。

アーキテクチャ上の依存方向、module ownership、table ownership、テスト戦略、Android platform 基準は `docs/architecture/` を正本とする。設計判断の理由と変更履歴は `docs/adr/` を正本とする。

データベースの現在version、module一覧、table一覧、依存ライブラリversion、CIコマンドなどコードから一意に決まる値はこの仕様書へ複製しない。現在値は次を参照する。

- database schema: `app/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt`
- module一覧: `settings.gradle.kts`
- table ownership: `config/architecture/table-ownership.tsv`
- CI: `.github/workflows/`
- Android platform: `docs/architecture/platform.md`
