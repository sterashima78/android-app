# Mosaic リポジトリ Wiki の編集方針

- 本文・見出し・要約を日本語で記述する。識別子、パス、コマンドは原文を保つ。
- この Wiki は現在のコードとテストを調べるための案内・説明である。ユーザー仕様は `docs/spec.md` と `docs/spec/`、設計は `docs/architecture/`、判断履歴は `docs/adr/`、作業手順は `AGENTS.md` と `docs/development-workflow.md` を正本とする。
- 正本を全文複製せず、責務・処理の流れ・境界・変更時の調査先を説明し、関連する正本、実装、テストへ相対リンクを付ける。
- `settings.gradle.kts` に登録された各 Gradle module に個別の説明ページを持たせる。feature の Domain / Data / UI、app の executable / composition / presentation、core、開発ツールもそれぞれ対象とし、`modules/` 配下に配置する。単なるディレクトリやシンボルの一覧ではなく、その module の責務、代表的な入口と処理の流れ、依存先との境界、所有する状態と失敗時の扱い、変更時に確認する実装・テストを説明する。
- module の追加・削除・移動時には説明ページと quickstart の案内も更新する。個別ページから関連 module と既存の横断的な architecture / workflow ページへリンクし、全体像と詳細を往復できるようにする。
- 実装と文書が食い違う場合は、両方の根拠を示す。コードだけを理由に仕様・設計を確定しない。
- 重要な振る舞い、所有権、依存方向、永続化、失敗時の扱いには現在のリポジトリ内の根拠を記録する。
- 設定値・スキーマ番号などは必要なときだけ記載し、変更されやすい数値や全シンボルの一覧を重複管理しない。
- リポジトリの Knowledge 機能と、この開発用 OpenWiki を区別して説明する。
- 認証情報、実ユーザーデータ、ローカル環境の絶対パス、生成ログ、ビルド成果物を Wiki に含めない。
- 更新は Codex の OpenWiki 連携から行う。OpenWiki 用の GitHub Actions workflow は不要であり、無効化した雛形もリポジトリに残さない。生成ツールが `.github/workflows/openwiki-update.yml` や定期更新を前提とする案内を再作成した場合は、生成完了後に取り除く。
