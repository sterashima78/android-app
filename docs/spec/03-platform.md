# 3. 対象環境

- Android 15（API 35）以降を対象とする。
- compile / target API は Android API 36 系とする。
- 配布対象CPUは arm64-v8a とする。
- Kotlin と Jetpack Compose を主要実装技術とする。
- Game の数独とクロンダイクでは、既存 Android アプリへ組み込んだ Godot runtime を利用する。
- ユーザー向け名称は Mosaic とする。
- 既存インストールとの互換性のため application id `dev.terashima.yomitorirss` と内部 database file 名 `yomitori-rss.db` は維持する。
